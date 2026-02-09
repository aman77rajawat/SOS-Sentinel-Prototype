import express from "express"
import dotenv from "dotenv"
import cors from "cors"
import mongoose from "mongoose"
import { createServer } from 'node:http';
import { Server } from 'socket.io';


dotenv.config()

const app = express();
const PORT = process.env.PORT;
const server = createServer(app);

const io = new Server(server, {
  cors: {
    origin: "*"
  }
});

app.use(cors())

let connectedUsers = []
let DroneList = []
let activeSOSRequests = [] 

// Socket Server
io.on('connection', (socket) => {
  console.log('User connected:', socket.id);

  
  socket.on("register-user", ({ username, lat, lon, alt }) => {
    let userdata = { 
      id: socket.id, 
      username: username,
      lat: lat || 0,
      lon: lon || 0,
      alt: alt || 0,
      type: 'user'
    }
    connectedUsers.push(userdata)
    io.emit("connected-users", connectedUsers);
    console.log(`User registered: ${username} at (${lat}, ${lon})`);
  });

  socket.on("register-drone", ({ droneId, droneName, lat, lon, alt, status }) => {
    let droneData = {
      id: socket.id,
      droneId: droneId,
      droneName: droneName,
      lat: lat || 0,
      lon: lon || 0,
      alt: alt || 0,
      status: status || 'idle', 
      assignedUser: null,
      type: 'drone'
    }
    DroneList.push(droneData)
    io.emit("drone-list", DroneList);
    console.log(`Drone registered: ${droneName} at (${lat}, ${lon})`);
  });

  // User sends SOS signal On server
  socket.on("send-sos", ({ username, lat, lon, alt, message }) => {
    const sosRequest = {
      userId: socket.id,
      username: username,
      lat: lat,
      lon: lon,
      alt: alt || 0,
      message: message || "Emergency assistance needed",
      timestamp: Date.now(),
      status: 'pending'
    }
    
    activeSOSRequests.push(sosRequest)
    
    // Notify all drones about the SOS
    io.emit("sos-alert", sosRequest);
    
    // Send confirmation to user
    socket.emit("sos-sent", { 
      success: true, 
      message: "SOS signal sent to all available drones",
      sosRequest 
    });
    
    console.log(`SOS received from ${username} at (${lat}, ${lon})`);
  });

  // User updates their location (real-time tracking)
  socket.on("update-location", ({ lat, lon, alt }) => {
    // Update user location in connectedUsers
    const userIndex = connectedUsers.findIndex(user => user.id === socket.id)
    if (userIndex !== -1) {
      connectedUsers[userIndex].lat = lat
      connectedUsers[userIndex].lon = lon
      connectedUsers[userIndex].alt = alt || 0
      
      // Find if this user has an active SOS
      const sosIndex = activeSOSRequests.findIndex(sos => sos.userId === socket.id)
      if (sosIndex !== -1) {
        activeSOSRequests[sosIndex].lat = lat
        activeSOSRequests[sosIndex].lon = lon
        activeSOSRequests[sosIndex].alt = alt || 0
        
        // Send updated location to assigned drone
        const assignedDrone = DroneList.find(drone => drone.assignedUser === socket.id)
        if (assignedDrone) {
          io.to(assignedDrone.id).emit("target-location-updated", {
            userId: socket.id,
            username: connectedUsers[userIndex].username,
            lat: lat,
            lon: lon,
            alt: alt || 0
          });
        }
      }
    }
  });

  // Drone accepts SOS and starts following
  socket.on("accept-sos", ({ userId }) => {
    const droneIndex = DroneList.findIndex(drone => drone.id === socket.id)
    const sosIndex = activeSOSRequests.findIndex(sos => sos.userId === userId)
    
    if (droneIndex !== -1 && sosIndex !== -1) {
      // Update drone status
      DroneList[droneIndex].status = 'responding'
      DroneList[droneIndex].assignedUser = userId
      
      // Update SOS status
      activeSOSRequests[sosIndex].status = 'assigned'
      
      // Notify the user that help is on the way
      io.to(userId).emit("sos-accepted", {
        droneId: DroneList[droneIndex].droneId,
        droneName: DroneList[droneIndex].droneName,
        droneLocation: {
          lat: DroneList[droneIndex].lat,
          lon: DroneList[droneIndex].lon,
          alt: DroneList[droneIndex].alt
        }
      });
      
      // Send target location to drone
      const userLocation = activeSOSRequests[sosIndex]
      socket.emit("navigate-to-target", {
        userId: userId,
        username: userLocation.username,
        lat: userLocation.lat,
        lon: userLocation.lon,
        alt: userLocation.alt
      });
      
      io.emit("drone-list", DroneList);
      console.log(`Drone ${DroneList[droneIndex].droneName} responding to ${userLocation.username}`);
    }
  });

  // Drone updates its location
  socket.on("drone-location-update", ({ lat, lon, alt, status }) => {
    const droneIndex = DroneList.findIndex(drone => drone.id === socket.id)
    if (droneIndex !== -1) {
      DroneList[droneIndex].lat = lat
      DroneList[droneIndex].lon = lon
      DroneList[droneIndex].alt = alt || 0
      if (status) DroneList[droneIndex].status = status
      
      // If drone is tracking a user, send location update to that user
      if (DroneList[droneIndex].assignedUser) {
        io.to(DroneList[droneIndex].assignedUser).emit("drone-location-update", {
          droneId: DroneList[droneIndex].droneId,
          lat: lat,
          lon: lon,
          alt: alt,
          status: status
        });
      }
      
      io.emit("drone-list", DroneList);
    }
  });

  // Drone arrives at user location
  socket.on("arrived-at-target", ({ userId }) => {
    const droneIndex = DroneList.findIndex(drone => drone.id === socket.id)
    if (droneIndex !== -1) {
      DroneList[droneIndex].status = 'arrived'
      
      io.to(userId).emit("drone-arrived", {
        droneId: DroneList[droneIndex].droneId,
        droneName: DroneList[droneIndex].droneName
      });
      
      io.emit("drone-list", DroneList);
    }
  });

  // Complete SOS mission
  socket.on("complete-sos", ({ userId }) => {
    const droneIndex = DroneList.findIndex(drone => drone.id === socket.id)
    const sosIndex = activeSOSRequests.findIndex(sos => sos.userId === userId)
    
    if (droneIndex !== -1) {
      DroneList[droneIndex].status = 'idle'
      DroneList[droneIndex].assignedUser = null
      io.emit("drone-list", DroneList);
    }
    
    if (sosIndex !== -1) {
      activeSOSRequests[sosIndex].status = 'completed'
      io.to(userId).emit("sos-completed", {
        message: "Mission completed. Stay safe!"
      });
    }
  });

  // Cancel SOS
  socket.on("cancel-sos", () => {
    const sosIndex = activeSOSRequests.findIndex(sos => sos.userId === socket.id)
    if (sosIndex !== -1) {
      const assignedDrone = DroneList.find(drone => drone.assignedUser === socket.id)
      if (assignedDrone) {
        assignedDrone.status = 'idle'
        assignedDrone.assignedUser = null
        io.to(assignedDrone.id).emit("sos-cancelled", {
          message: "SOS has been cancelled by user"
        });
      }
      
      activeSOSRequests.splice(sosIndex, 1)
      socket.emit("sos-cancelled-confirm", { success: true })
    }
  });

  // Get active SOS requests
  socket.on("get-sos-requests", () => {
    socket.emit("sos-requests-list", activeSOSRequests);
  });

  // Private messaging (existing functionality)
  socket.on('private_message', ({ from, to, message }) => {
    if (to) {
      io.to(to).emit('private_message', {
        to,
        from,
        message,
        timestamp: Date.now(),
      });
    }
  });

  socket.on('message_seen', ({ senderId }) => {
    const senderSocketId = connectedUsers.find(u => u.id === senderId)?.id;
    if (senderSocketId) {
      io.to(senderSocketId).emit('message_seen_ack', {
        seen: true,
        timestamp: Date.now(),
      });
    }
  });

  // Disconnect handling
  socket.on('disconnect', () => {
    // Remove from users
    const userIndex = connectedUsers.findIndex(user => user.id === socket.id)
    if (userIndex !== -1) {
      const user = connectedUsers[userIndex]
      
      // Cancel any active SOS from this user
      const sosIndex = activeSOSRequests.findIndex(sos => sos.userId === socket.id)
      if (sosIndex !== -1) {
        const assignedDrone = DroneList.find(drone => drone.assignedUser === socket.id)
        if (assignedDrone) {
          assignedDrone.status = 'idle'
          assignedDrone.assignedUser = null
        }
        activeSOSRequests.splice(sosIndex, 1)
      }
      
      connectedUsers.splice(userIndex, 1)
      io.emit("connected-users", connectedUsers);
    }
    
    // Remove from drones
    const droneIndex = DroneList.findIndex(drone => drone.id === socket.id)
    if (droneIndex !== -1) {
      const drone = DroneList[droneIndex]
      
      // Notify assigned user if drone disconnects
      if (drone.assignedUser) {
        io.to(drone.assignedUser).emit("drone-disconnected", {
          droneId: drone.droneId,
          message: "Drone has disconnected"
        });
      }
      
      DroneList.splice(droneIndex, 1)
      io.emit("drone-list", DroneList);
    }
    
    console.log('User disconnected:', socket.id);
  });
});

app.use(express.json())

app.get('/', (req, res) => {
  res.send({ "status": "Server Running" })
})




server.listen(PORT, () => console.log(`Server running on port ${PORT}`));