/*
  RC Car Control Firmware for Arduino Uno
  
  Manages motor control for a 4WD chassis via an L293D motor driver,
  receiving commands over Bluetooth from an HC-05 module.

  Hardware Connections:
  - HC-05 TX -> Arduino Pin 10 (SoftwareSerial RX)
  - HC-05 RX -> Arduino Pin 11 (SoftwareSerial TX)
  
  - L293D Steering (M1):
    - IN1: Pin 2
    - IN2: Pin 3
    - EN1: Pin 5 (PWM)
  
  - L293D Propulsion (M3):
    - IN3: Pin 4
    - IN4: Pin 6
    - EN3: Pin 7 (PWM)

  - Built-in LED: Pin 13 (for diagnostics)

  Communication Protocol:
  - 'F': Propulsion forward
  - 'B': Propulsion backward
  - 'L': Steering left
  - 'R': Steering right
  - 'S': Stop all motors (propulsion halt, steering neutral)
  - Any other character is treated as 'S'.
*/

#include <SoftwareSerial.h>

// Pin definitions using const for type safety and memory optimization
const uint8_t STEERING_IN1_PIN = 2;
const uint8_t STEERING_IN2_PIN = 3;
const uint8_t STEERING_ENABLE_PIN = 5; // PWM

const uint8_t PROPULSION_IN3_PIN = 4;
const uint8_t PROPULSION_IN4_PIN = 6;
const uint8_t PROPULSION_ENABLE_PIN = 7; // PWM

const uint8_t BT_RX_PIN = 10;
const uint8_t BT_TX_PIN = 11;

const uint8_t LED_PIN = 13;

// Full speed for motors
const uint8_t MOTOR_SPEED = 255;

// SoftwareSerial for Bluetooth communication
SoftwareSerial hc05(BT_RX_PIN, BT_TX_PIN);

void setup() {
  // Initialize hardware serial for debugging (optional)
  Serial.begin(9600);
  Serial.println("Firmware starting...");

  // Initialize Bluetooth serial communication
  hc05.begin(9600);

  // Set motor control pins to output mode
  pinMode(STEERING_IN1_PIN, OUTPUT);
  pinMode(STEERING_IN2_PIN, OUTPUT);
  pinMode(STEERING_ENABLE_PIN, OUTPUT);
  pinMode(PROPULSION_IN3_PIN, OUTPUT);
  pinMode(PROPULSION_IN4_PIN, OUTPUT);
  pinMode(PROPULSION_ENABLE_PIN, OUTPUT);

  // Set LED pin to output mode
  pinMode(LED_PIN, OUTPUT);

  // Ensure all motors are stopped at startup
  stopAllMotors();

  // Perform self-diagnostic boot sequence
  selfDiagnosticBoot();
  
  Serial.println("Setup complete. Waiting for commands...");
}

void loop() {
  // Check if data is available from the Bluetooth module
  if (hc05.available()) {
    char command = hc05.read();
    Serial.print("Received command: ");
    Serial.println(command);
    
    processCommand(command);
  }
}

// Processes the received command and controls motors accordingly
void processCommand(char cmd) {
  // Acknowledge valid command reception by toggling the LED
  if (cmd == 'F' || cmd == 'B' || cmd == 'L' || cmd == 'R' || cmd == 'S') {
    acknowledgeCommand();
  }

  switch (cmd) {
    case 'F':
      moveForward();
      break;
    case 'B':
      moveBackward();
      break;
    case 'L':
      turnLeft();
      break;
    case 'R':
      turnRight();
      break;
    case 'S':
    default: // Treat any unknown character as a stop command for safety
      stopAllMotors();
      break;
  }
}

// --- Motor Control Functions ---

void moveForward() {
  // Set propulsion motor to forward
  digitalWrite(PROPULSION_IN3_PIN, HIGH);
  digitalWrite(PROPULSION_IN4_PIN, LOW);
  analogWrite(PROPULSION_ENABLE_PIN, MOTOR_SPEED);
  // Keep steering neutral
  setSteering(LOW, LOW, 0);
}

void moveBackward() {
  // Set propulsion motor to backward
  digitalWrite(PROPULSION_IN3_PIN, LOW);
  digitalWrite(PROPULSION_IN4_PIN, HIGH);
  analogWrite(PROPULSION_ENABLE_PIN, MOTOR_SPEED);
  // Keep steering neutral
  setSteering(LOW, LOW, 0);
}

void turnLeft() {
  // Set steering motor to left
  setSteering(HIGH, LOW, MOTOR_SPEED);
  // Stop propulsion
  setPropulsion(LOW, LOW, 0);
}

void turnRight() {
  // Set steering motor to right
  setSteering(LOW, HIGH, MOTOR_SPEED);
  // Stop propulsion
  setPropulsion(LOW, LOW, 0);
}

void stopAllMotors() {
  // Stop propulsion motor
  setPropulsion(LOW, LOW, 0);
  // Stop steering motor (returns to neutral)
  setSteering(LOW, LOW, 0);
}

// --- Helper Functions ---

// Helper to control the steering motor
void setSteering(uint8_t in1, uint8_t in2, uint8_t speed) {
  digitalWrite(STEERING_IN1_PIN, in1);
  digitalWrite(STEERING_IN2_PIN, in2);
  analogWrite(STEERING_ENABLE_PIN, speed);
}

// Helper to control the propulsion motor
void setPropulsion(uint8_t in3, uint8_t in4, uint8_t speed) {
  digitalWrite(PROPULSION_IN3_PIN, in3);
  digitalWrite(PROPULSION_IN4_PIN, in4);
  analogWrite(PROPULSION_ENABLE_PIN, speed);
}

// --- Diagnostics ---

// Blinks the built-in LED to confirm firmware is alive on boot
void selfDiagnosticBoot() {
  for (int i = 0; i < 3; i++) {
    digitalWrite(LED_PIN, HIGH);
    delay(500);
    digitalWrite(LED_PIN, LOW);
    delay(500);
  }
}

// Briefly toggles the LED to acknowledge a valid command
void acknowledgeCommand() {
  digitalWrite(LED_PIN, HIGH);
  delay(50);
  digitalWrite(LED_PIN, LOW);
}
