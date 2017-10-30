#include <Servo.h>
#include "NewPing.h"

// Communication modes MOnitor, COntrol, or NOne until requested.
#define MO_COMM          ('M')
#define CO_COMM          ('C')
#define NO_COMM          ('N')

// Commands to control the rc car
#define SPEED_COMMAND    (0)
#define STEERING_COMMAND (1)
#define NUM_COMMANDS     (2)

// UNO pins for speed and steering commands. D10 and D9, respectively.
#define PIN_SPEED_COMMAND    (10)
#define PIN_STEERING_COMMAND (9)

// Displacement per shaft revolution (meter)
#define CAR_SHAFT_DPR (0.095f)

// Sensor pulses per shaft revolution
#define CAR_SHAFT_PPR (8)

// Critical speed commands measured from the receiver
#define SPEED_COMMAND_NEUTRAL    (1400)
#define SPEED_COMMAND_FORWARD    (SPEED_COMMAND_NEUTRAL - 250)
#define SPEED_COMMAND_BACKWARD   (SPEED_COMMAND_NEUTRAL + 250)

// Criticial steering commands measured from the receiver
#define STEERING_COMMAND_NEUTRAL (1568)
#define STEERING_COMMAND_RIGHT   (STEERING_COMMAND_NEUTRAL - 530)
#define STEERING_COMMAND_LEFT    (STEERING_COMMAND_NEUTRAL + 530)

//#define ENABLE_DISTANCE
#define PIN_DISTANCE_ECHO (6)
#define PIN_DISTANCE_TRIG (7)
#define MAX_DISTANCE 400

NewPing sonar(PIN_DISTANCE_TRIG, PIN_DISTANCE_ECHO, MAX_DISTANCE); // NewPing setup of pins and maximum distance.
unsigned int pingSpeed = 25; // How frequently are we going to send out a ping (in milliseconds). 25ms would be 40 times a second.
unsigned long pingTimer;     // Holds the next ping time.
float distance;
float newDistance;

// These are sent to Android
int speedCommand;
int steeringCommand;

// Commands come from either Android through bluetooth or the RC receiver
float serial[NUM_COMMANDS];
volatile int receiver[NUM_COMMANDS];

// Interrupt timing
unsigned long interruptTime;

// RC receiver interrupt handling variables
unsigned long lastRisingTime[NUM_COMMANDS];
byte lastLevel[NUM_COMMANDS];

// Wheel encoder for speed measurement
volatile unsigned long pulse;
boolean wasHigh;
unsigned long lastReadTime;

// Main loop control variables
unsigned long currentTime;
unsigned long previousTime;
unsigned long deltaTime;
int frameCounter;

// Variables for handling serial communication
char mode;
char buffer[1024];
int index;

// Servo instances
Servo speedServo;
Servo steeringServo;

void setup()
{
  speedCommand = 0;
  steeringCommand = 0;

  mode = MO_COMM;
  index = 0;

  deltaTime = 0UL;
  frameCounter = 0;
  currentTime = micros();
  previousTime = currentTime;

  pulse = 0;
  wasHigh = false;
  lastReadTime = currentTime;

  speedServo.attach(PIN_SPEED_COMMAND);
  steeringServo.attach(PIN_STEERING_COMMAND);

  pinMode(PIN_DISTANCE_TRIG, OUTPUT); // Sets the PIN_DISTANCE_TRIG as an Output
  pinMode(PIN_DISTANCE_ECHO, INPUT); // Sets the PIN_DISTANCE_ECHO as an Input

  pinMode(13, OUTPUT); // Shows current mode (true means control)

  Serial.begin(115200);

  steeringServo.writeMicroseconds(STEERING_COMMAND_NEUTRAL);
  speedServo.writeMicroseconds(SPEED_COMMAND_NEUTRAL);
  delay(3000);

  PCIFR |= (1 << PCIE1);
  PCICR |= (1 << PCIE1);
  PCMSK1 |= (1 << PCINT0); //A0
  PCMSK1 |= (1 << PCINT1); //A1
  PCMSK1 |= (1 << PCINT2); //A2

  pingTimer = millis();
}

/* Parses communication mode and serial commands. */
void parse()
{
  char *p = buffer;
  char cmd[10];
  int i = 0;
  int j = 0;

  if (*p == MO_COMM || *p == CO_COMM || *p == NO_COMM) {
    mode = *p;
    if (mode == CO_COMM)
      digitalWrite(13, HIGH);
    else
      digitalWrite(13, LOW);
    return;
  }
  while (true) {
    if (*p == ' ') {
      serial[j++] = atof(cmd);
      i = 0;
    } else if (*p == '\0') {
      serial[j] = atof(cmd);
      break;
    } else {
      cmd[i++] = *p;
      cmd[i] = '\0';
    }
    p++;
  }
}

/* Reads valid commands or communication mode request. */
void serialRead()
{
  int c = 0;
  while (Serial.available() > 0) {
    c = Serial.read();
    if (c == '\n') {
      buffer[index] = '\0';
      parse();
      index = 0;
      break;
    } else {
      buffer[index++] = (char)c;
    }
  }
}

/* Sends speed, steering angle and their corresponding recent commands */
void serialWrite()
{
  Serial.println(String(speedCommand)
                 + " " + String(steeringCommand)
                 + " " + String(distance));
}

/* Distance measurement with ultrasonic sensor */
void echoCheck() { // Timer2 interrupt calls this function every 24uS where you can check the ping status.
  // Don't do anything here!
  if (sonar.check_timer()) { // This is how you check to see if the ping was received.
    newDistance = sonar.ping_result / US_ROUNDTRIP_CM;
    if (newDistance != NO_ECHO) { 
      distance = (distance * 3.0 + newDistance) / 4.0;
    }
  }
}

void loop()
{
  currentTime = micros();
  deltaTime = currentTime - previousTime;

#ifdef ENABLE_DISTANCE
  // Notice how there's no delays in this sketch to allow you to do other processing in-line while doing distance pings.
  if (millis() >= pingTimer) {   // pingSpeed milliseconds since last ping, do another ping.
    pingTimer += pingSpeed;      // Set the next ping time.
    sonar.ping_timer(echoCheck); // Send out the ping, calls "echoCheck" function every 24uS where you can check the ping status.
  }
#endif

  if (deltaTime >= 1000) {
    ++frameCounter;
    // 1000 Hz Task

    if (frameCounter % 40 == 0) {
      // 25 Hz Task
      serialRead();
      if (mode == CO_COMM) {
        speedCommand = serial[SPEED_COMMAND];
        steeringCommand = serial[STEERING_COMMAND];
      } else if (mode == MO_COMM) {
        speedCommand = receiver[SPEED_COMMAND];
        steeringCommand = receiver[STEERING_COMMAND];
      } else {
        speedCommand = SPEED_COMMAND_NEUTRAL;
        steeringCommand = STEERING_COMMAND_NEUTRAL;
      }
      speedServo.writeMicroseconds(speedCommand);
      steeringServo.writeMicroseconds(steeringCommand);
    }
    if (frameCounter % 20 == 0 && mode != NO_COMM) {
      // 50 Hz Task
      serialWrite();
    }
    previousTime = currentTime;
  }
}

// ISR (PCINT0_vect) pin change interrupt for D8 to D13
// ISR (PCINT1_vect) pin change interrupt for A0 to A5
// ISR (PCINT2_vect) pin change interrupt for D0 to D7
// PINB (digital pin 8 to 13)
// PINC (analog input pins)
// PIND (digital pins 0 to 7)

// A0 - CH2 of my receiver - throttle
// A1 - CH1 of my receiver - steering
// A2 - Wheel encoder
ISR(PCINT1_vect) {
  interruptTime = micros();

  // Speed command (aka throttle)
  if ((lastLevel[SPEED_COMMAND] == LOW) && (PINC & B00000001)) {
    lastLevel[SPEED_COMMAND] = HIGH;
    lastRisingTime[SPEED_COMMAND] = interruptTime;
  } else if ((lastLevel[SPEED_COMMAND] == HIGH) && !(PINC & B00000001)) {
    lastLevel[SPEED_COMMAND] = LOW;
    receiver[SPEED_COMMAND] = interruptTime - lastRisingTime[SPEED_COMMAND];
  }

  // Steering command
  if ((lastLevel[STEERING_COMMAND] == LOW) && (PINC & B00000010)) {
    lastLevel[STEERING_COMMAND] = HIGH;
    lastRisingTime[STEERING_COMMAND] = interruptTime;
  } else if ((lastLevel[STEERING_COMMAND] == HIGH) && !(PINC & B00000010)) {
    lastLevel[STEERING_COMMAND] = LOW;
    receiver[STEERING_COMMAND] = interruptTime - lastRisingTime[STEERING_COMMAND];
  }

  // Wheel encoder
  if (!(PINC & B00000100)) {
    if (wasHigh) {
      ++pulse;
    }
    wasHigh = false;
  } else {
    wasHigh = true;
  }
}

