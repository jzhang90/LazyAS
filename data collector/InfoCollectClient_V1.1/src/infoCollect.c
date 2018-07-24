#include<pcap.h>
#include<stdlib.h>
#include<string.h>
#include<errno.h>
#include<sys/socket.h>
#include<linux/ip.h>
#include<linux/tcp.h>
#include<time.h>
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
#include"network.h"
#include"buffer.h"
#include"hashtable.h"
#include"ap_survey.h"

#define AGE_TIMEOUT 30
#define KEYLEN 25
char interface[30];
char ip[30];

static pthread_mutex_t mtx = PTHREAD_MUTEX_INITIALIZER;  
static pthread_cond_t cond = PTHREAD_COND_INITIALIZER;
Record *cur_task = NULL;

hash_table sessionTable,stationTable;
struct DSQueue * sendQueue;

int MAXSIZE = 65535;/*发送缓冲最大size*/
char *interface_name[2] = {"wlan0","wlan1"};
int last_time[2] = {0,0};
long long last_tb[2] = {0,0};
long long last_rb[2] = {0,0};


int getInfo(Record *record)
{	
	char get_type_cmd[50];
	sprintf(get_type_cmd,"%s%s","iw dev wlan0 station dump | grep ",record->sta_mac);	 
	FILE *fstream=NULL;  	
    if(NULL==(fstream=popen(get_type_cmd,"r")))    
    {   
		fprintf(stderr,"get_type_cmd: %s",strerror(errno));    		
       	return 0;    
    }

	char result[1024];  
    memset(result,0,sizeof(result));  
	
	if(NULL!=fgets(result, sizeof(result), fstream))
	{
		
		record->mode = 0;
	}
	else
	{
				
		record->mode = 1;		
	}
	pclose(fstream);
	
	/*au*/
	char au_cmd[100],rssi_cmd[100];
	sprintf(au_cmd,"iw dev %s survey dump|grep time |awk '{print $4}'",interface_name[record->mode]);
	if(NULL==(fstream=popen(au_cmd,"r")))    
    {   
        fprintf(stderr,"execute command failed: %s",strerror(errno));    
        return 0;    
    } 
	int active_time = -1, buzy_time = -1;
	memset(result,0,sizeof(result));
	if(NULL!=fgets(result, sizeof(result), fstream))
	{
		active_time = atoi(result);
	}
	
	memset(result,0,sizeof(result));
	if(NULL!=fgets(result, sizeof(result), fstream))
	{
		buzy_time = atoi(result);
	}			

	pclose(fstream);
	if(active_time==-1 || buzy_time==-1)
		return 0;
	record->au = ((double)buzy_time)/((double)active_time);
	
	/*rssi*/
	sprintf(rssi_cmd,"%s%s%s%s%s","iw dev ",interface_name[record->mode]," station get ",record->sta_mac,"|grep signal:|awk '{print $2}'");
	
   	if(NULL==(fstream=popen(rssi_cmd,"r")))    
    {   
        fprintf(stderr,"rssi_cmd: %s",strerror(errno));    
        return 0;    
    }
	int rssi = 1;	
	memset(result,0,sizeof(result));
   	if(NULL!=fgets(result, sizeof(result), fstream))   
    {   
		rssi= atoi(result);	
		//printf("rcmd is %s,result is %s,rssi is %d\n",rssi_cmd,result,rssi);
    }	
    pclose(fstream);  
	if(rssi == 1)
	{
		return 0;
	}	
	record->rssi = rssi;
	
	/*br*/
	char br_cmd[100];
	sprintf(br_cmd,"iwinfo %s info|grep 'Bit Rate:'|awk '{print $3}'",interface_name[record->mode]);
	
   	if(NULL==(fstream=popen(br_cmd,"r")))    
    {   
        fprintf(stderr,"br_cmd: %s",strerror(errno));    
        return 0;    
    }
	double br = -1;	
	memset(result,0,sizeof(result));
	if(NULL!=fgets(result, sizeof(result), fstream))   
    {   
		br = atof(result);	
    }	
    pclose(fstream); 
	if(br == -1)
		return 0;
	record->br = br;
	
	//对应终端吞吐率
	char tt_cmd[100];
	sprintf(tt_cmd,"iw %s station get %s |grep 'tx bytes'|awk -F ':' '{print $2}'",interface_name[record->mode],record->sta_mac);
	
	char rt_cmd[100];
	sprintf(rt_cmd,"iw %s station get %s |grep 'rx bytes'|awk -F ':' '{print $2}'",interface_name[record->mode],record->sta_mac);
	
	time_t cur_time;
	time(&cur_time);
	record->rt = 0;
	record->tt = 0;
	int value_size;
	ThroughputRecord *t_Record = ht_get(&stationTable, record->sta_mac, sizeof(record->sta_mac), &value_size);   
   
	if(t_Record == NULL)
	{
		ThroughputRecord r;		
		r.last_time = cur_time;
			
		if(NULL ==(fstream = popen(tt_cmd,"r")))    
    	{   
	        fprintf(stderr,"td_cmd: %s",strerror(errno)); 			
	        return 0;    
	    }			
		long long tt = -1;
		memset(result,0,sizeof(result));
		if(NULL!=fgets(result, sizeof(result), fstream))   
	    {   
			sscanf(result,"%lld",&tt);	
	    }	
	    pclose(fstream);  
		
		if(NULL==(fstream=popen(rt_cmd,"r")))    
	    {   
	        fprintf(stderr,"rd_cmd: %s",strerror(errno));  			
	        return 0;    
	    }		
		long long rt = -1;
		memset(result,0,sizeof(result));
		if(NULL!=fgets(result, sizeof(result), fstream))   
	    {   
			sscanf(result,"%lld",&rt);	
	    }	
	    pclose(fstream);  		
		if(rt == -1 || tt == -1)
		{			
			return 0;
		}	
		r.tt = tt;
		r.rt = rt;
		ht_insert(&stationTable,record->sta_mac,sizeof(record->sta_mac),&r,sizeof(r));
		return 0;
	}
	int time_gap = cur_time- t_Record->last_time;	
	if(time_gap >= 10)
	{			
   		if(NULL ==(fstream = popen(tt_cmd,"r")))    
    	{   
	        fprintf(stderr,"td_cmd: %s",strerror(errno)); 
			ht_remove(&stationTable, record->sta_mac, sizeof(record->sta_mac));
	        return 0;    
	    }			
		long long tt = -1;
		memset(result,0,sizeof(result));
		if(NULL!=fgets(result, sizeof(result), fstream))   
	    {   
			sscanf(result,"%lld",&tt);	
	    }	
	    pclose(fstream);  
		
		if(NULL==(fstream=popen(rt_cmd,"r")))    
	    {   
	        fprintf(stderr,"rd_cmd: %s",strerror(errno));  
			ht_remove(&stationTable, record->sta_mac, sizeof(record->sta_mac));
	        return 0;    
	    }		
		long long rt = -1;
		memset(result,0,sizeof(result));
		if(NULL!=fgets(result, sizeof(result), fstream))   
	    {   
			sscanf(result,"%lld",&rt);	
	    }	
	    pclose(fstream); 		
		if(rt == -1 || tt == -1)
		{
			ht_remove(&stationTable, record->sta_mac, sizeof(record->sta_mac));
			return 0;
		}		
		//printf("%lld,%lld,%d\n",tt,t_Record->tt,time_gap);
		record->tt = (tt-t_Record->tt)/(time_gap);
		record->rt = (rt-t_Record->rt)/(time_gap);
		t_Record->tt = tt;
		t_Record->rt = rt;
		t_Record->last_time = cur_time;
    	return 1;	
		
	}
	else 
		return 0;
	/*网卡tt & rt*/
	/*char tt_cmd[100];
	sprintf(tt_cmd,"iw %s station get %s |grep 'tx bytes'|awk -F ':' '{print $2}'",interface_name[record->mode],record->sta_mac);
	//sprintf(tt_cmd,"ifconfig %s |grep 'bytes'|awk  '{print $6}'|awk -F ':' '{print $2}'",interface_name[record->mode]);
	char rt_cmd[100];
	sprintf(rt_cmd,"iw %s station get %s |grep 'rx bytes'|awk -F ':' '{print $2}'",interface_name[record->mode],record->sta_mac);
	//sprintf(rt_cmd,"ifconfig %s |grep 'bytes'|awk  '{print $2}'|awk -F ':' '{print $2}'",interface_name[record->mode]);	
	time_t cur_time;
	time(&cur_time);
	record->rt = 0;
	record->tt = 0;
	if(cur_time - last_time[record->mode] >= 10)
	{		
   		if(NULL ==(fstream = popen(tt_cmd,"r")))    
    	{   
	        fprintf(stderr,"td_cmd: %s",strerror(errno));    
	        return 0;    
	    }			
		long long tt = -1;
		memset(result,0,sizeof(result));
		if(NULL!=fgets(result, sizeof(result), fstream))   
	    {   
			sscanf(result,"%lld",&tt);	
	    }	
	    pclose(fstream);  
		
		if(NULL==(fstream=popen(rt_cmd,"r")))    
	    {   
	        fprintf(stderr,"rd_cmd: %s",strerror(errno));    
	        return 0;    
	    }		
		long long rt = -1;
		memset(result,0,sizeof(result));
		if(NULL!=fgets(result, sizeof(result), fstream))   
	    {   
			sscanf(result,"%lld",&rt);	
	    }	
	    pclose(fstream);  
		
		if(rt == -1 || tt == -1)
		{
			return 0;
		}	
		time_t cur_time;
		time(&cur_time);
		if(last_time[record->mode] != 0)
		{
			record->tt = (tt-last_tb[record->mode])/(cur_time-last_time[record->mode]);
			record->rt = (rt-last_rb[record->mode])/(cur_time-last_time[record->mode]);
			last_time[record->mode] = cur_time;
			last_tb[record->mode] = tt;
			last_rb[record->mode] = rt;
	    	return 1;
		}
		else
		{
			last_time[record->mode] = cur_time;
			last_tb[record->mode] = tt;
			last_rb[record->mode] = rt;
			return 0;

		}
	}
	else 
		return 1;*/
	
}


void mac2String(unsigned char *mac,char *mac_string)
{
    memset(mac_string,0,18);
    sprintf(mac_string, "%02x:%02x:%02x:%02x:%02x:%02x",mac[0],mac[1],mac[2],mac[3],mac[4],mac[5]);
}


/*
捕获数据包后的处理*/
void captureOne(u_char *arg,const struct pcap_pkthdr *pkthdr,const u_char *packet)
{	
	char key_char[KEYLEN];
	memset(key_char,0,KEYLEN);
    struct ether_header *eth_hdr = (struct ether_header*)packet;
    struct iphdr *ip_hdr = (struct iphdr*)(packet + sizeof(struct ether_header));
    struct tcphdr *tcp_hdr = (struct tcphdr*)(packet + sizeof(struct ether_header) + ip_hdr->ihl * 4);    
	
    if(tcp_hdr->dest < tcp_hdr->source)
    {
     	sprintf(key_char,"%04x%04x",tcp_hdr->dest,tcp_hdr->source);
    }
    else
    {
    	sprintf(key_char,"%04x%04x",tcp_hdr->source,tcp_hdr->dest);
    }
	
    if(ip_hdr->daddr < ip_hdr->saddr)
    {
    	sprintf(key_char + 8,"%08x%08x",ip_hdr->daddr,ip_hdr->saddr);
    }
    else
    {
    	sprintf(key_char + 8,"%08x%08x",ip_hdr->saddr,ip_hdr->daddr);
    }	 
	
    if((tcp_hdr->syn & 1) && (tcp_hdr->ack & 1))
    {    
    	struct timeval t;
		t.tv_sec = pkthdr->ts.tv_sec;
		t.tv_usec = pkthdr->ts.tv_usec;
        ht_insert(&sessionTable, key_char, KEYLEN,&t, sizeof(struct timeval));  
		return;
    }
	 
    if((tcp_hdr->ack & 1) && ht_contains(&sessionTable, key_char, KEYLEN))
    {
     	Record *record = (Record*)malloc(sizeof(Record));		
    	mac2String(eth_hdr->ether_shost,record->sta_mac);
		int value_size = 0;
      	struct timeval *t = ht_get(&sessionTable, key_char, KEYLEN, &value_size);   
       	record->delay = (pkthdr->ts.tv_sec - t->tv_sec) * 1000000 + (pkthdr->ts.tv_usec - t->tv_usec);
		ht_remove(&sessionTable, key_char, KEYLEN);
		if(pthread_mutex_trylock(&mtx) == 0)
		{		
			cur_task = record;
			pthread_cond_signal(&cond);
			pthread_mutex_unlock(&mtx);
		}else
		{
			free(record);
		}
		//
    }
	
}
void* getArgs()
{
	while(1)
    {
		pthread_mutex_lock(&mtx);
		while(cur_task == NULL)
		{		
			pthread_cond_wait(&cond, &mtx);
		}	
		
		if(getInfo(cur_task))
		{ 
			//printf("record is %s %d %d %f %d %f\n",record->sta_mac,record->mode,record->rssi,record->au,record->delay,record->br);
				
            ds_queue_put(sendQueue,cur_task);//存入发送缓冲       
			
		}else
		{
			free(cur_task);
        }
		cur_task = NULL;
		pthread_mutex_unlock(&mtx);
		
	}
}
/*
发送线程
*/
void* sendData(void *sockfd)
{
	int fd = *(int*)sockfd;
    while(1)
    {
        Record *record = ds_queue_get(sendQueue);		
		sendRecord(fd,record);
		free(record);
    }
}
int main(int argc, char *argv[])
{
	if (argc < 3)
	{		
		printf("please input the interface or ip\n");
		exit(-1);	
	}
	strcpy(interface,argv[1]);
	strcpy(ip,argv[2]);
    char errBuf[PCAP_ERRBUF_SIZE];
    getMac(local_mac);    
    pcap_t * device = pcap_open_live(interface, 65535, 1, 0, errBuf);

    if(!device)
    {
        printf("error: pcap_open_live(): %s\n", errBuf);
        exit(1);
    }
 
    struct bpf_program filter;
    //pcap_compile(device, &filter, "tcp && less 64 && (tcp[tcpflags] & (tcp-syn|tcp-ack) != 0)", 1, 0);
    pcap_compile(device, &filter, "(tcp[tcpflags] & (tcp-syn|tcp-ack) != 0)", 1, 0);
    pcap_setfilter(device, &filter);
	ht_init(&sessionTable, HT_NONE, 0.05);//hash表自己申请 自己释放
	ht_init(&stationTable, HT_NONE, 0.05);//hash表自己申请 自己释放
	sendQueue = ds_queue_create(MAXSIZE);   
 
    int id = 0, sockfd;
    initNetwork(&sockfd);
    pthread_t thread1,thread2;
	pthread_create(&thread2,NULL,getArgs,NULL); 
    pthread_create(&thread1,NULL,sendData,&sockfd);     
    pcap_loop(device, -1, captureOne, (u_char*)&id);
    pcap_close(device);
    closeNetwork(sockfd);
    return 0;
}
