#ifndef DATARECEIVER_H_
#define DATARECEIVER_H_
#include<iostream>
#include<list>
#include"test.h"
#include"receivebuffer.h"
#include<pthread.h>
#include<mysql.h>

using namespace std;


class DataReceiver
{
private :
	static MYSQL my_connection;

    int server;

    static  ReceiveBuffer* buffer;

    bool initServer();

    static void print_record(struct Record *record);

    static void print_mac(unsigned char *mac);

    static struct Record * get_record();

    static  pthread_mutex_t mutex;
	static  pthread_mutex_t mutex2;

    static  pthread_cond_t full;

    static   pthread_cond_t empty;
	static  void* heartBeat(void *arg);

public :

    DataReceiver();

    static  void *receive(void *client);

    static  void* save(void *arg);

    void run();

};

#endif
