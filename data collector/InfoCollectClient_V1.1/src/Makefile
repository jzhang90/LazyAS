OBJS = infoCollect.o ap_survey.o buffer.o hashtable.o network.o murmur.o
CC = gcc
client:${OBJS} 
	${CC} -o InfoCollectClient ${OBJS} -lpthread -lpcap
infoCollect.o:infoCollect.c buffer.h hashtable.h network.h ap_survey.h
	${CC} -c $< -lpcap -lpthread -D__WITH_MURMUR
ap_survey.o:ap_survey.c ap_survey.h
	${CC} -c $<
buffer.o:buffer.c buffer.h
	${CC} -c $<
hashtable.o:hashtable.c hashtable.h murmur.h hashfunc.h
	${CC} -c $< -D__WITH_MURMUR
network.o:network.c network.h
	${CC} -c $<
murmur.o:murmur.c murmur.h hashfunc.h
	${CC} -c $< -D__WITH_MURMUR
clean:
	rm -f InfoCollectClient ${OBJS}

