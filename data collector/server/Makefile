OBJS = main.o DataReceiver.o ReceiveBuffer.o 

server:${OBJS} 
	g++ -o server ${OBJS} -lpthread -I/usr/lib/mysql -lmysqlclient
main.o:main.cpp datareceiver.h
	g++ -c   -I/usr/include/mysql $< -L/usr/lib/mysql -lmysqlclient
DataReceiver.o:DataReceiver.cpp datareceiver.h receivebuffer.h
	g++ -lpthread -c   -I/usr/include/mysql $< -L/usr/lib/mysql -lmysqlclient
ReceiverBuffer.o:ReceiveBuffer.cpp receivebuffer.h
	g++ -c $<

clean:
	rm -f server ${OBJS}

