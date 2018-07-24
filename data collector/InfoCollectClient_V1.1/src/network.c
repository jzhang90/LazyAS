#include"network.h"

#define SERVER_PORT 7777

void initNetwork(int *socketfd)
{
    *socketfd=socket(AF_INET,SOCK_STREAM,0);
    if(*socketfd==-1)
    {
        printf("socket create falilure\n");
        exit(-1);
    }
    struct sockaddr_in serverAddr;
    serverAddr.sin_family = AF_INET;
    serverAddr.sin_port = htons(SERVER_PORT);
    serverAddr.sin_addr.s_addr = inet_addr(ip);

    if(connect(*socketfd,(struct sockaddr*)&serverAddr,sizeof(struct sockaddr)) == -1)
    {
        perror("connect");
        exit(-1);
    }
    printf("connect sucess\n");
}

/*
 *Author:layrong
 *Name:
 *Description:����һ����¼
 */

void sendRecord(int socketfd, Record* record)
{
    write(socketfd,record,sizeof(Record));
    //printf("send record\n");
}

/*
 *Author:layrong
 *Name:
 *Description:�ر���������
 */

void closeNetwork(int socketfd)
{
    close(socketfd);
}

/*
 *Author:layrong
 *Name:get_mac
 *Description:��ȡ�����ص�mac��ַ ���ڽ��бȽ����ݰ�������
 */
 char local_mac[30];


int getMac(char* mac)
{
    int sockfd;
    struct ifreq tmp;
    sockfd = socket(AF_INET, SOCK_STREAM, 0);
    if( sockfd < 0)
    {
        perror("create socket fail\n");
        return -1;
    }

    memset(&tmp,0,sizeof(struct ifreq));
    strncpy(tmp.ifr_name,interface,sizeof(tmp.ifr_name)-1);

    if( (ioctl(sockfd,SIOCGIFHWADDR,&tmp)) < 0 )
    {
        printf("mac ioctl error\n");
        return -1;
    }
    sprintf(mac, "%02x%02x%02x%02x%02x%02x",
            (unsigned char)tmp.ifr_hwaddr.sa_data[0],
            (unsigned char)tmp.ifr_hwaddr.sa_data[1],
            (unsigned char)tmp.ifr_hwaddr.sa_data[2],
            (unsigned char)tmp.ifr_hwaddr.sa_data[3],
            (unsigned char)tmp.ifr_hwaddr.sa_data[4],
            (unsigned char)tmp.ifr_hwaddr.sa_data[5]
           );
    printf("local mac:%s\n", mac);
    close(sockfd);
    return 0;
}

