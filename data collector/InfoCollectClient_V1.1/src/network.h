
#ifndef NETWORK_H_
#define NETWORK_H_
#include<stdlib.h>
#include<string.h>
#include<errno.h>
#include<sys/socket.h>
#include<linux/ip.h>
#include<linux/tcp.h>
#include<sys/types.h>
#include<stdio.h>
#include<sys/time.h>
#include<signal.h>
#include<pthread.h>
#include<unistd.h>
#include<netinet/if_ether.h>
#include<malloc.h>
#include<sys/ioctl.h>
#include<netinet/in.h>
#include<net/if.h>
#include<net/if_arp.h>
#include<arpa/inet.h>

typedef struct Record
{
    char sta_mac[18];
    int rssi;
    int mode;
    double au;
    int delay;
	double br;
	long long tt;
	long long rt;
	//double td;//发送速率
	//double rd;//接收速率
}Record;

typedef struct 
{
	long long tt;
	long long rt;
	time_t last_time;
}ThroughputRecord;

void initNetwork(int *socketfd);

void sendRecord(int socketfd, Record* record);

void closeNetwork(int socketfd);

int getMac(char* mac);

extern char local_mac[];
extern char interface[];
extern char ip[];

#endif

