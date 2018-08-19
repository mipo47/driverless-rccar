import socket
import select
import sys
import os
import datetime
import cv2
import numpy as np
import win32api as wapi
import time
import threading

APP_NAME = "stream"
HOST = "192.168.1.67"
# HOST = "83.180.236.94"
# HOST = "192.168.200.127"
PORT = 5000
TIMEOUT = 5  # seconds
SAVE_DATASET = False

VIDEO_STREAM = False
DATASET_VIDEO = 'dataset.avi'

CONTROL_SEND_INTERVAL = 1.0 / 60  # 60 times per second
SPEED_NEUTRAL = 1422
SPEED_MIN = 900
SPEED_MAX = 1948
STEERING_NEUTRAL = 1392
STEERING_MIN = STEERING_NEUTRAL - 482  # right
STEERING_MAX = STEERING_NEUTRAL + 482  # left
TURN_AMOUNT = 0.004
ACCELERATE_AMOUNT = 0.0018
BRAKE_AMOUNT = ACCELERATE_AMOUNT


def lerp(value, min, neutral, max, value_min=-1.0, value_neutral=0.0, value_max=1.0):
    value = value - value_neutral
    if value >= 0.0:
        value /= value_max - value_neutral
    else:
        value /= value_neutral - value_min

    result = neutral
    if value > 0:
        result = neutral * (1.0 - value) + max * value
    elif value < 0:
        result = neutral * (1.0 + value) + min * -value
    return np.clip(result, min, max)


def infinite_loop(target):
    while True:
        target()


class Stream:
    def __init__(self):
        self.socket, self.ip = None, None
        self.ping_time = None

        self.image_id = 0
        self.keys = [
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
            'F',
            'C', 'M',
            'W', 'A', 'S', 'D'
        ]
        self.key_down = {}

        self.control = False
        self.control_time = None
        self.control_sent = None
        self.speed = 0  # -1 to 1
        self.steering = 0  # -1 to 1

        self.data = None
        self.data_name = "datasets/" + datetime.datetime.now().strftime("%Y-%m-%d_%H_%M_%S")

        self.capture = None
        self.video_file = None

    def start(self, start_image_id=0):
        self.image_id = start_image_id
        t = threading.Thread(target=infinite_loop, args=(self.key_check,))
        t.start()

        if VIDEO_STREAM:
            self.video_file = open(DATASET_VIDEO, "wb")

        while True:
            server_socket = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            print("UDP socket connecting to", HOST, PORT)
            self.socket = server_socket
            self.socket_address = (HOST, PORT)
            try:
                self.ping()
                self.send_command("Q;10")
                self.connection(self.socket)
                self.socket.close()
            except (ConnectionResetError, ConnectionAbortedError):
                print("Client closed connection")
                if self.socket is None:
                    self.socket.close()

    def ping(self):
        # send ping signal each second
        now = datetime.datetime.now().timestamp()
        if self.ping_time is None or now - self.ping_time > 1:
            self.ping_time = now
            self.send_command("P")

    def send_command(self, command):
        if self.socket is None or self.socket_address is None:
            print("Simulate send", command)
        else:
            print(self.socket_address, command)
            command = "[" + command + "]"
            b = bytearray()
            b.extend(map(ord, command))
            try:
                self.socket.sendto(b, self.socket_address)
            except:
                print("Cannot send command", command)

    def key_check(self):
        key_presssed = {}
        for key in self.keys:
            old_down = self.key_down[key] if key in self.key_down else False
            new_down = wapi.GetAsyncKeyState(ord(key))
            self.key_down[key] = new_down

            # key was just released
            key_presssed[key] = old_down and not new_down

        for q in range(10):
            if key_presssed[str(q)]:
                quality = q * 10 if q > 0 else 5
                self.send_command('Q;{}'.format(quality))
                break

        if key_presssed['F']:
            self.send_command("F")

        if key_presssed['C']:
            self.send_command("A;C")
            self.control = True
        elif key_presssed['M']:
            self.send_command("A;M")
            self.control = False

        if self.control:
            current_time = datetime.datetime.now().timestamp()
            elapsed = current_time - self.control_time if self.control_time else 0.1
            elapsed *= 1000

            if self.key_down['A']:
                self.steering += elapsed * TURN_AMOUNT / (1.0 + np.abs(self.speed*2))
            if self.key_down['D']:
                self.steering -= elapsed * TURN_AMOUNT / (1.0 + np.abs(self.speed*2))

            if self.key_down['S']:
                self.speed -= elapsed * BRAKE_AMOUNT
            elif self.key_down['W']:
                self.speed += elapsed * ACCELERATE_AMOUNT

            self.speed = np.clip(self.speed, -1.0, 1.0)
            self.steering = np.clip(self.steering, -1.0, 1.0)

            self.steering *= 0.995 ** elapsed
            self.speed *= 0.995 ** elapsed

            self.control_time = current_time
            # print(self.steering, self.speed)

            if self.control_sent is None or current_time - self.control_sent > CONTROL_SEND_INTERVAL:
                self.control_sent = current_time
                speed = lerp(self.speed, SPEED_MIN, SPEED_NEUTRAL, SPEED_MAX)
                steering = lerp(self.steering, STEERING_MIN, STEERING_NEUTRAL, STEERING_MAX)
                self.send_command("A;{:.0f} {:.0f}".format(speed, steering))

    def read_packet(self, conn, timeout, key_check=False):
        try:
            conn.setblocking(False)
        except:
            return False, None, None, None

        has_connection = True
        packet_i = 0
        buff = b''
        start_recv = datetime.datetime.now().timestamp()
        while True:
            if key_check:
                self.key_check()

            if datetime.datetime.now().timestamp() - start_recv > timeout:
                print("Client is frozen, reconnecting...")
                conn.close()
                return False, None, None, None
                break

            try:
                packet = conn.recv(510000)
            except socket.error:
                time.sleep(0.01)
                continue

            while packet_i < len(packet):
                c = bytes([packet[packet_i]])
                if c == b'$':
                    print("Client asked to disconnect")
                    conn.close()
                    has_connection = False
                    break
                elif c == b'[':
                    packet_i += 1
                    continue
                elif c == b']':
                    packet_i += 1
                    break
                else:
                    buff += c
                    packet_i += 1
            return has_connection, packet, packet_i, buff

    def connection(self, conn):
        time_step = 0
        timestamps = []
        fps = 0
        while True:
            time_step += 1
            has_connection, packet, packet_i, buff = self.read_packet(conn, TIMEOUT)
            if not has_connection:
                break

            decoded_buff = buff.decode()
            header = decoded_buff.split(';')

            # arduino_online = bool(int(header[0]))
            # speed_cmd = int(header[1])
            # steering_cmd = int(header[2])
            # distance = float(header[3])
            # size = int(header[4])
            # print(arduino_online, timestep, speed_cmd, steering_cmd, distance, size)
            # print(decoded_buff)

            img = bytearray(packet[packet_i:])
            if VIDEO_STREAM:
                self.video_file.write(img)
                self.video_file.flush()
                if time_step > 0:
                    if self.capture is None:
                        self.capture = cv2.VideoCapture(DATASET_VIDEO)

                    ret, frame = self.capture.read()
                    print("frame", ret, len(img))
                else:
                    frame = np.zeros((480, 720), dtype=np.uint8) + 128
            else:
                np_arr = np.frombuffer(img, dtype=np.uint8)
                frame = cv2.imdecode(np_arr, cv2.IMREAD_COLOR)

            if not self.control:
                self.ping()

            Stream.draw(header, frame, fps)

            timestamp = datetime.datetime.now().timestamp()
            if len(timestamps) > 0:
                fps = len(timestamps) / (timestamp - timestamps[0] + 1e-6)

            timestamps.append(timestamp)
            while len(timestamps) > 50:
                timestamps.pop(0)

            if SAVE_DATASET:
                self.write_to_dataset(header, img, timestamp)

    def write_to_dataset(self, header, image, timestamp):
        if self.data is None:
            os.makedirs(self.data_name)
            self.data = open(self.data_name + "/header.csv", 'a')
            self.data.write("timestamp,image_id,online,speed,steering,distance,size,acc_x,acc_y,acc_z,gyro_x,gyro_y,gyro_z,mag_x,mag_y,max_z,lat,lon\n")

        self.image_id += 1
        with open(self.data_name + "/{}.jpg".format(self.image_id), 'wb') as f:
            f.write(image)

        entry = "{},{},".format(timestamp, self.image_id) + ",".join(header) + "\n"
        self.data.write(entry)
        if self.image_id % 20 == 0:
            self.data.flush()

    @staticmethod
    def draw(header, frame, fps):
        distance = float(header[3])

        SENSOR_INDEX = 5
        for i in range(SENSOR_INDEX, SENSOR_INDEX + 3 * 3):
            value = float(header[i])
            x = int(720 / 2)
            y = int(20 + (i - SENSOR_INDEX) * 10)
            if i < SENSOR_INDEX + 3:  # ACCELEROMETER
                color = (0, 255, 0)
                x_value = int(x + value * 10)
            elif i < SENSOR_INDEX + 6:  # GYROSCOPE
                color = (0, 0, 255)
                x_value = int(x + value * 20)
            else:  # MAGNETIC_FIELD
                color = (255, 0, 0)
                x_value = int(x + value * 2)

            x_value = np.clip(x_value, 0, 720)
            cv2.line(frame, (x, y), (x_value, y), color, 2)

        # cv2.line(frame, (5, 5), (5 + int(distance * 2), 5), (255, 0, 255), 2)
        cv2.putText(frame,
                    "{:.0f}".format(fps), (10, 460),
                    cv2.FONT_HERSHEY_SIMPLEX, 1,
                    (255, 255, 255), 2, cv2.LINE_AA)

        speed_cmd = int(lerp(int(header[1]), -180, 0, 250, SPEED_MIN, SPEED_NEUTRAL, SPEED_MAX))
        steering_cmd = int(lerp(int(header[2]), -100, 0, 100, STEERING_MIN, STEERING_NEUTRAL, STEERING_MAX))
        cv2.line(frame, (360, 300), (360 - steering_cmd, 300 - speed_cmd), (0, 255, 255), 3, cv2.LINE_AA)
        cv2.line(frame, (360, 300), (360 - steering_cmd, 300 - speed_cmd), (0, 0, 0), 2, cv2.LINE_AA)
        cv2.line(frame, (360, 305), (360 - steering_cmd*2, 305), (100, 255, 100), 2)

        cv2.imshow(APP_NAME, frame)
        cv2.waitKey(1)

if __name__ == '__main__':
    stream = Stream()
    stream.start()
    # while True:
    #     stream.key_check()