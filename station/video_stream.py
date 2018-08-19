import cv2

vcap = cv2.VideoCapture("dataset.avi")
while(1):
    ret, frame = vcap.read()
    if frame is None:
        print("Noooooooo")
        break

    cv2.imshow('VIDEO', frame)
    cv2.waitKey(100)