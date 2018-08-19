import cv2

# file = open("video.avi", "wb")

FILENAME = 'video.avi'

video_writer = cv2.VideoWriter_fourcc(*'DIVX')
out = cv2.VideoWriter(FILENAME, video_writer, 25, (640, 480))
cap = cv2.VideoCapture(0)

cap2 = cv2.VideoCapture(FILENAME)

key = None
while key != ord('q'):
    _, frame = cap.read()
    out.write(frame)

    _, frame2 = cap2.read()
    cv2.imshow("video", frame2)
    key = cv2.waitKey(1)

cap.release()
cap2.release()
cv2.destroyAllWindows()