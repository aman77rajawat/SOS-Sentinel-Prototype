import math
import sys
import time
from pymavlink import mavutil

from config import (
    CONNECTION_STRING,
    SAFE_BATTERY_LIMIT,
    CRUISE_SPEED,
    ARRIVAL_RADIUS,
    CONTROL_RATE
)
class AutoDrone:
    def __init__(self):
        print(f"🔌 Connecting to {CONNECTION_STRING}...")
        try:
            self.vehicle = mavutil.mavlink_connection(CONNECTION_STRING)
            self.vehicle.wait_heartbeat(timeout=10)
            print(f"✅ Connected to System ID: {self.vehicle.target_system}")
        except Exception as e:
            print(f"❌ Connection Failed: {e}")
            sys.exit(1)

        # Initial Telemetry
        self.lat = 0.0
        self.lon = 0.0
        self.alt = 0.0
        self.battery = 100.0
        self.connected = True

    def stop(self):
        """Emergency Stop: Switch to LOITER to brake immediately."""
        if not self.connected: return
        print("\n⚠️ EMERGENCY STOP TRIGGERED ⚠️")
        print("-> Switching to LOITER (Brake)...")
        self.change_mode('LOITER')
        
        self.send_velocity_ned(0, 0, 0, 0)

    def update_telemetry(self):
        
       
        while True:
            msg = self.vehicle.recv_match(type=['GLOBAL_POSITION_INT', 'SYS_STATUS'], blocking=False)
            if not msg:
                break 
            
            if msg.get_type() == "GLOBAL_POSITION_INT":
                self.lat = msg.lat / 1e7
                self.lon = msg.lon / 1e7
                self.alt = msg.relative_alt / 1000.0

            if msg.get_type() == "SYS_STATUS":
                raw_bat = msg.battery_remaining
                if raw_bat <= 0: 
                    self.battery = 100
                else:
                    self.battery = raw_bat

    # -------- MATH UTILS --------
    def get_distance_metres(self, lat2, lon2):
        """Haversine Formula"""
        R = 6371000
        phi1 = math.radians(self.lat)
        phi2 = math.radians(lat2)
        dphi = math.radians(lat2 - self.lat)
        dlambda = math.radians(lon2 - self.lon)

        a = math.sin(dphi/2)**2 + math.cos(phi1)*math.cos(phi2)*math.sin(dlambda/2)**2
        c = 2 * math.atan2(math.sqrt(a), math.sqrt(1-a))
        return R * c

    def get_bearing(self, lat2, lon2):
        """Returns bearing in Radians from -pi to +pi"""
        if abs(lat2 - self.lat) < 1e-7 and abs(lon2 - self.lon) < 1e-7:
            return 0.0

        phi1 = math.radians(self.lat)
        phi2 = math.radians(lat2)
        dlambda = math.radians(lon2 - self.lon)

        y = math.sin(dlambda) * math.cos(phi2)
        x = math.cos(phi1)*math.sin(phi2) - math.sin(phi1)*math.cos(phi2)*math.cos(dlambda)
        return math.atan2(y, x) 

    def change_mode(self, mode_name):
        mode_id = self.vehicle.mode_mapping().get(mode_name)
        if mode_id is None: return
        
        self.vehicle.mav.set_mode_send(
            self.vehicle.target_system,
            mavutil.mavlink.MAV_MODE_FLAG_CUSTOM_MODE_ENABLED,
            mode_id
        )

    def arm_and_takeoff(self, target_alt):
        print("-> Checking pre-flight safety...")
        self.update_telemetry()
        if self.battery < SAFE_BATTERY_LIMIT:
            raise Exception(f"Battery too low: {self.battery}%")

        print(f"-> Arming and Taking Off to {target_alt}m...")
        self.change_mode("GUIDED")
        
        # Arm
        self.vehicle.mav.command_long_send(
            self.vehicle.target_system, self.vehicle.target_component,
            mavutil.mavlink.MAV_CMD_COMPONENT_ARM_DISARM, 0, 1, 0, 0, 0, 0, 0, 0
        )
        self.vehicle.motors_armed_wait()
        print("✅ Motors Armed")

        # Takeoff
        self.vehicle.mav.command_long_send(
            self.vehicle.target_system, self.vehicle.target_component,
            mavutil.mavlink.MAV_CMD_NAV_TAKEOFF, 0, 0, 0, 0, 0, 0, 0, target_alt
        )
        
        while True:
            self.update_telemetry()
            print(f"   Climbing: {self.alt:.1f}m", end='\r')
            if self.alt >= target_alt * 0.95:
                print(f"\n✅ Altitude Reached: {self.alt:.1f}m")
                break
            time.sleep(0.5)

    def send_velocity_ned(self, vx, vy, vz, yaw_rads):
        
        self.vehicle.mav.set_position_target_local_ned_send(
            0,
            self.vehicle.target_system,
            self.vehicle.target_component,
            mavutil.mavlink.MAV_FRAME_LOCAL_NED,
            0b100111000111, # Optimized Mask: Enable Vel (bits 3-5) and Yaw (bit 10)
            0, 0, 0,        # Pos (Ignored)
            vx, vy, vz,     # Velocity
            0, 0, 0,        # Accel (Ignored)
            yaw_rads, 0     # Yaw (Enabled), YawRate (Ignored)
        )

    def fly_to_coords(self, target_lat, target_lon):
        print(f"🧭 Flying to {target_lat}, {target_lon}")
        
        while True:
            self.update_telemetry()
            dist = self.get_distance_metres(target_lat, target_lon)
            
            # Use radians for both math and Yaw command
            bearing_rad = self.get_bearing(target_lat, target_lon)
            bearing_deg = math.degrees(bearing_rad) % 360

            print(f"   Dist: {dist:.1f}m | Bat: {self.battery}% | Hdg: {bearing_deg:.0f}°", end='\r')

            # 1. Safety Checks
            if self.battery < 20:
                print("\n⚠️ LOW BATTERY: Returning to Launch")
                self.change_mode('RTL')
                return
            
            # 2. Arrival Check
            if dist < ARRIVAL_RADIUS:
                print(f"\n✅ Target Reached (within {ARRIVAL_RADIUS}m)")
                break

            # 3. Calculate Velocity Vector (NED Frame)
            # X = North (cos), Y = East (sin)
            vx = CRUISE_SPEED * math.cos(bearing_rad)
            vy = CRUISE_SPEED * math.sin(bearing_rad)
            
            # 4. Send Synchronized Command (Vel + Yaw)
            self.send_velocity_ned(vx, vy, 0, bearing_rad)
            
            time.sleep(CONTROL_RATE)

    def run(self):
        try:
            print("⏳ Waiting for GPS Fix...")
            # Loop until we get non-zero coordinates
            while self.lat == 0.0 and self.lon == 0.0:
                self.update_telemetry()
                time.sleep(1)
                print(f"   Waiting for GPS... (Bat: {self.battery}%)", end='\r')
            
            print(f"\n✅ Current Position {self.lat:.6f}, {self.lon:.6f}")
            
            user_input = input("Enter Target Lat, Lon: ") # after change it to real socket listener for coords 
            t_lat, t_lon = map(float, user_input.split(","))

        
            dist = self.get_distance_metres(t_lat, t_lon)
            flight_alt = max(10, min(50, dist * 0.1))
            
            self.arm_and_takeoff(flight_alt)
            self.fly_to_coords(t_lat, t_lon)
            
            print("🏁 Mission Complete. Returning to Launch.")
            self.change_mode("RTL")

        except KeyboardInterrupt:
            # The graceful exit
            self.stop()
        except Exception as e:
            print(f"\n❌ Error: {e}")
            self.stop()

if __name__ == "__main__":
    pilot = AutoDrone()
    pilot.run()