#ifndef TEST_H_
#define TEST_H_
#include <time.h>
#include<stdint.h>

#define CHAR_SIZE 50
#define SERVER_IP "10.21.1.113"
#define SERVER_PORT 7777

struct Record
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
};
void receive_data();


#endif
