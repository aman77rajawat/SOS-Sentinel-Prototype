# 🚁 SOS-Sentinel: Autonomous Pilot System

**Branch:** `pilot-dev`\
**Role:** Autonomous Drone Control Logic

This repository contains the **"Brain"** of the SOS-Sentinel system. It
is a Python-based autonomous pilot that communicates with
ArduPilot/Pixhawk drones via **MAVLink**. It handles telemetry
monitoring, fail-safe logic, and dynamic coordinate navigation.

------------------------------------------------------------------------

## 🛠️ Tech Stack

-   **Language:** Python 3.7+\
-   **Protocol:** MAVLink v2.0\
-   **Library:** `pymavlink` (Standard ArduPilot Library)\
-   **Simulation:** ArduPilot SITL (Software In The Loop)\
-   **GCS:** Mission Planner

------------------------------------------------------------------------

## 🚀 Setup & Installation

### 1️⃣ Clone the Repository

``` bash
git clone https://github.com/aman77rajawat/SOS-Sentinel-Prototype.git
cd SOS-Sentinel-Prototype
git checkout pilot-dev
```

------------------------------------------------------------------------

### 2️⃣ Install Dependencies

``` bash
pip install -r requirements.txt
```

OR:

``` bash
pip install pymavlink
```

------------------------------------------------------------------------

## 🎮 How to Run (Simulation Guide)

### Step 1️⃣ Start Simulation

1.  Open Mission Planner\
2.  Go to Simulation tab\
3.  Click Multirotor\
4.  Wait for:
    -   GPS: 3D Fix\
    -   Ready to Arm\
    -   Drone visible on map

------------------------------------------------------------------------

### Step 2️⃣ Create the MAVLink Bridge

-   Ctrl + F → Temp screen\
-   Click Mavlink\
-   Configure row:

Type: UDP Client\
Direction: Outbound\
Port: 14552\
Host: 127.0.0.1\
Write: Checked

Click Start

------------------------------------------------------------------------

### Step 3️⃣ Run the Pilot

``` bash
python src/smart_pilot.py
```

------------------------------------------------------------------------

## 🕹️ Usage

The script will connect and wait for a valid GPS lock.

It will display: ✅ Current Position: -35.363...

Enter Coordinates: Input your target latitude and longitude when prompted.

Example: -35.362, 149.166

Autonomous Flight:

The drone will arm and takeoff to a calculated altitude (based on distance).

It will fly to the target while monitoring battery and heading.

Upon arrival, it will auto-trigger RTL (Return to Launch).

------------------------------------------------------------------------

## 🛡️ Safety Features
Battery Failsafe: Mission aborts immediately if battery < 30%.

Emergency Stop: Press Ctrl + C at any time to trigger immediate LOITER (Air Brake).

SITL Bypass: Automatically detects simulation environment (0% battery bug) and overrides it to 100% for testing.  
