

import express from "express";
import dotenv from "dotenv";
import cors from "cors";
import { createServer } from "node:http";
import { Server } from "socket.io";

dotenv.config();

const app = express();
const PORT = process.env.PORT || 5000;

app.use(cors());
app.use(express.json());

const server = createServer(app);

const io = new Server(server, {
  cors: { origin: "*" },
});

// ================================
// MEMORY STORAGE
// ================================
let connectedUsers = [];
let DroneList = [];
let activeSOSRequests = [];

// ================================
// SOCKET SERVER
// ================================
io.on("connection", (socket) => {
  console.log("User connected:", socket.id);

  // ================================
  // REGISTER USER
  // ================================
  socket.on("register-user", ({ username, lat, lon, alt }) => {

    // remove duplicates (FIX)
    connectedUsers = connectedUsers.filter(u => u.id !== socket.id);

    const userdata = {
      id: socket.id,
      username,
      lat: lat || 0,
      lon: lon || 0,
      alt: alt || 0,
      type: "user",
    };

    connectedUsers.push(userdata);

    io.emit("connected-users", connectedUsers);
    console.log(`User registered: ${username}`);
  });

  // ================================
  // REGISTER DRONE
  // ================================
  socket.on("register-drone", ({ droneId, droneName, lat, lon, alt, status }) => {

    // remove duplicates (FIX)
    DroneList = DroneList.filter(d => d.id !== socket.id);

    const droneData = {
      id: socket.id,
      droneId,
      droneName,
      lat: lat || 0,
      lon: lon || 0,
      alt: alt || 0,
      status: status || "idle",
      assignedUser: null,
      type: "drone",
    };

    DroneList.push(droneData);

    io.emit("drone-list", DroneList);
    console.log(`Drone registered: ${droneName}`);
  });

  // ================================
  // SEND SOS
  // ================================
  socket.on("send-sos", ({ username, lat, lon, alt, message }) => {

    // prevent duplicate SOS from same user
    const alreadyExists = activeSOSRequests.find(
      s => s.userId === socket.id && s.status !== "completed"
    );
    if (alreadyExists) return;

    const sosRequest = {
      userId: socket.id,
      username,
      lat,
      lon,
      alt: alt || 0,
      message: message || "Emergency assistance needed",
      timestamp: Date.now(),
      status: "pending",
    };

    activeSOSRequests.push(sosRequest);

    io.emit("sos-alert", sosRequest);

    socket.emit("sos-sent", {
      success: true,
      message: "SOS sent",
      sosRequest,
    });

      console.log(`SOS received from ${username}`);
      //added
      console.log("Lat:", lat);
      console.log("Lon:", lon);
      console.log("Alt:", alt);

  });

  // ================================
  // UPDATE USER LOCATION
  // ================================
  socket.on("update-location", ({ lat, lon, alt }) => {

  // Added THIS (live debug)
  console.log("LIVE LOCATION UPDATE");
  console.log("Lat:", lat);
  console.log("Lon:", lon);
  console.log("Alt:", alt);

  const user = connectedUsers.find(u => u.id === socket.id);
  if (!user) return;

  user.lat = lat;
  user.lon = lon;
  user.alt = alt || 0;

  const sos = activeSOSRequests.find(s => s.userId === socket.id);

  if (sos) {
    sos.lat = lat;
    sos.lon = lon;
    sos.alt = alt || 0;

    const assignedDrone = DroneList.find(
      d => d.assignedUser === socket.id
    );

    if (assignedDrone) {
      io.to(assignedDrone.id).emit("target-location-updated", {
        userId: socket.id,
        username: user.username,
        lat,
        lon,
        alt: alt || 0,
      });
    }
  }
});


  // ================================
  // DRONE ACCEPT SOS
  // ================================
  socket.on("accept-sos", ({ userId }) => {

    const drone = DroneList.find(d => d.id === socket.id);
    const sos = activeSOSRequests.find(s => s.userId === userId);

    if (!drone || !sos) return;

    // prevent double accept (FIX)
    if (sos.status !== "pending") return;

    drone.status = "responding";
    drone.assignedUser = userId;
    sos.status = "assigned";

    io.to(userId).emit("sos-accepted", {
      droneId: drone.droneId,
      droneName: drone.droneName,
      droneLocation: {
        lat: drone.lat,
        lon: drone.lon,
        alt: drone.alt,
      },
    });

    socket.emit("navigate-to-target", sos);

    io.emit("drone-list", DroneList);
  });

  // ================================
  // DRONE LOCATION UPDATE
  // ================================
  socket.on("drone-location-update", ({ lat, lon, alt, status }) => {
    const drone = DroneList.find(d => d.id === socket.id);
    if (!drone) return;

    drone.lat = lat;
    drone.lon = lon;
    drone.alt = alt || 0;
    if (status) drone.status = status;

    if (drone.assignedUser) {
      io.to(drone.assignedUser).emit("drone-location-update", {
        droneId: drone.droneId,
        lat,
        lon,
        alt,
        status,
      });
    }

    io.emit("drone-list", DroneList);
  });

  // ================================
  // ARRIVED
  // ================================
  socket.on("arrived-at-target", ({ userId }) => {
    const drone = DroneList.find(d => d.id === socket.id);
    if (!drone) return;

    drone.status = "arrived";

    io.to(userId).emit("drone-arrived", {
      droneId: drone.droneId,
      droneName: drone.droneName,
    });

    io.emit("drone-list", DroneList);
  });

  // ================================
  // COMPLETE SOS
  // ================================
  socket.on("complete-sos", ({ userId }) => {
    const drone = DroneList.find(d => d.id === socket.id);
    const sosIndex = activeSOSRequests.findIndex(
      s => s.userId === userId
    );

    if (drone) {
      drone.status = "idle";
      drone.assignedUser = null;
    }

    if (sosIndex !== -1) {
      activeSOSRequests[sosIndex].status = "completed";
      io.to(userId).emit("sos-completed");
    }

    io.emit("drone-list", DroneList);
  });

  // ================================
  // CANCEL SOS
  // ================================
  socket.on("cancel-sos", () => {
    const sosIndex = activeSOSRequests.findIndex(
      s => s.userId === socket.id
    );

    if (sosIndex === -1) return;

    const assignedDrone = DroneList.find(
      d => d.assignedUser === socket.id
    );

    if (assignedDrone) {
      assignedDrone.status = "idle";
      assignedDrone.assignedUser = null;

      io.to(assignedDrone.id).emit("sos-cancelled");
    }

    activeSOSRequests.splice(sosIndex, 1);

    socket.emit("sos-cancelled-confirm", { success: true });
  });

  // ================================
  // DISCONNECT
  // ================================
  socket.on("disconnect", () => {
    connectedUsers = connectedUsers.filter(u => u.id !== socket.id);
    DroneList = DroneList.filter(d => d.id !== socket.id);

    activeSOSRequests = activeSOSRequests.filter(
      s => s.userId !== socket.id
    );

    io.emit("connected-users", connectedUsers);
    io.emit("drone-list", DroneList);

    console.log("User disconnected:", socket.id);
  });
});

// ================================
app.get("/", (req, res) => {
  res.send({ status: "Server Running" });
});

// ================================
// server.listen(PORT, () =>
//   console.log(`Server running on port ${PORT}`)
// );

//for real phone testing use this 
server.listen(PORT, "0.0.0.0", () => {
  console.log(`Server running on http://0.0.0.0:${PORT}`);
});
