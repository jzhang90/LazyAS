/*Description:receive the packet from client               *
 *Author     :layrong                                      *
 *Data       :2014-4-14                                    *
 *Mail       :nilayrong@163.com                            *
 ***********************************************************/
#include<sys/types.h>
#include<sys/socket.h>
#include<stdio.h>
#include<stdlib.h>
#include<string.h>
#include<netinet/in.h>
#include<arpa/inet.h>
#include<iostream>
#include<list>
#include<pthread.h>
#include"datareceiver.h"
#include<unistd.h>
#include <netinet/if_ether.h>
#include<malloc.h>
#include<time.h>

/*
 *Author:layrong
 *Name:
 *Description:同步变量
 */
pthread_mutex_t DataReceiver::mutex = PTHREAD_MUTEX_INITIALIZER;
pthread_mutex_t DataReceiver::mutex2 = PTHREAD_MUTEX_INITIALIZER;
pthread_cond_t DataReceiver::full = PTHREAD_COND_INITIALIZER;
pthread_cond_t DataReceiver::empty = PTHREAD_COND_INITIALIZER;
ReceiveBuffer* DataReceiver::buffer=new ReceiveBuffer();
MYSQL DataReceiver::my_connection;


/*
 *Author:layrong
 *Name:
 *Description:打印mac地址
 */
void DataReceiver::print_mac(unsigned char  *mac)
{

    int i=ETHER_ADDR_LEN;
    while(i>0)
    {
        printf("%s%x",(i==ETHER_ADDR_LEN)?"":":",(*mac++));
        i--;
    }
    printf("\n");

}


/*
 *Author:layrong
 *Name:
 *Description:打印记录
 */
void DataReceiver::print_record(struct Record* record)
{
    printf(" -------------the record is -----------------\n");
    printf("sta_mac: %s",record->sta_mac);
    printf("au: %f",record->au);
    printf("mode: %d",record->mode);
    printf("rssi: %d",record->rssi);
    printf("delay: %d",record->delay);
	printf("br:%f",record->br);
	printf("tt:%lld",record->tt);
	printf("rt:%lld",record->rt);
    printf("\n");
}


/*
 *Author:layrong
 *Name:
 *Description:初始化服务器端
 */

bool DataReceiver::initServer()
{
    server=socket(AF_INET,SOCK_STREAM,0);
    if(-1==server)
    {

        printf("create error\n");
        exit(-1);
    }
    cout<<"create success\n";
    struct sockaddr_in addr;

    addr.sin_family=AF_INET;
    addr.sin_port=htons(SERVER_PORT);
    addr.sin_addr.s_addr=inet_addr(SERVER_IP);
    if(bind(server,(struct sockaddr*)&addr,sizeof(struct sockaddr))==-1)
    {
        printf("bind error\n");
        exit(-1);
    }
    cout<<"bind success\n";

    if(listen(server,10)==-1)
    {
        cout<<"listen failure"<<endl;
        exit(-1);
    }
    cout<<"listen success"<<endl;
    //printf("socket:%d\n",server);
}


/*
 *Author:layrong
 *Name:
 *Description:接收数据函数，对应接收线程
 */
void* DataReceiver::receive(void *client)
{
    struct Record *record=get_record();
    int i=0;

    while(read(*((int*)client),record,sizeof(struct Record)))
    {
        pthread_mutex_lock(&mutex);
        if(buffer->IsFull())
        {
            cout<<"buffer is full\n";
            pthread_cond_wait(&full,&mutex);
        }
        cout<<"receive"<<endl;
        print_record(record);
        buffer->Add(record);
        pthread_cond_signal(&empty);
        pthread_mutex_unlock(&mutex);
        record=get_record();
    }
	cout<<"close "<<*(int*)(client)<<endl;
    close(*(int*)(client));
}


/*
 *Author:layrong
 *Name:
 *Description:通过host获取主域名
 */
char * get_host_domain(char *host)
{
    char* cur_pos=strchr(host,'.');
    if(cur_pos==NULL) return host;
    else if(*cur_pos=='.') return ++cur_pos;
}



struct Record * DataReceiver::get_record()
{
    struct Record* record=(struct Record *)malloc(sizeof(struct Record));
    return record;
}
/*
 *Author:layrong
 *Name:
 *Description:保存数据函数，对应于保存线程 从缓冲中取出数据并保存到数据库中
 */
void * DataReceiver::save(void *arg)
{
    struct Record *record=NULL;
    char *sql;
    struct in_addr addr_1;
    char sta_mac[13];
    int rssi,mode;
    double au;      
    int res;   
    //int i=0;

    while(1)
    {
        pthread_mutex_lock(&mutex);
        if(buffer->IsEmpty())
        {
            cout<<"buffer is empty\n";
            pthread_cond_wait(&empty,&mutex);
        }

       	cout<<"save"<<endl;
        record=buffer->Get();
        //cout<<i++<<endl;
        print_record(record);
        sql=(char*)malloc(200*sizeof(char));
        memset(sta_mac,'\0',13);
        sprintf(sql,"insert into InfoCollect values('%s','%d','%d','%f','%d','%f','%lld','%lld')",record->sta_mac,record->rssi,record->mode,record->au,record->delay,record->br,record->tt,record->rt);

        //printf("sql is %s\n",sql);
        pthread_mutex_lock(&mutex2);
        res=mysql_query(&my_connection,sql);

        if(res)
            printf("%s\n",mysql_error(&my_connection));
        mysql_commit(&my_connection);
		pthread_mutex_unlock(&mutex2);
        //delete record;
        pthread_cond_signal(&empty);
        pthread_mutex_unlock(&mutex);

        //free(record);
        free(record);
        record=NULL;
        free(sql);

    }
    mysql_close(&my_connection);
}
DataReceiver:: DataReceiver()
{

}


/*
 *Author:layrong
 *Name:
 *Description:主控执行函数
 */
 void* DataReceiver::heartBeat(void *arg)
 {
	while(1)
	{
		pthread_mutex_lock(&mutex2);
		mysql_ping(&my_connection);
		pthread_mutex_unlock(&mutex2);
		sleep(3600);
	}
		
}
void DataReceiver::run()
{
    initServer();
    pthread_t thread1,thread2,thread3;
    struct sockaddr_in clientaddr;
	mysql_init(&my_connection);
	if(mysql_real_connect(&my_connection,"localhost","root","songlei","InfoCollect",3306,NULL,0))
    {
        printf("connect success\n");
    }
    else
    {
        exit(-1);
    }
    pthread_create(&thread1,NULL,save,0);
	pthread_create(&thread3,NULL,heartBeat,0);
    int size=sizeof(clientaddr);
    //printf("server：%d\n",server);

    while(1)
    {
        int client=accept(server,(struct sockaddr*)&clientaddr,(socklen_t*)&size);
        if(client==-1)
        {
            printf("accept error\n");
            exit(-1);
        }
        cout<<"accept success"<<endl;
        pthread_create(&thread2,0,receive,&client);
    }
    close(server);
}

