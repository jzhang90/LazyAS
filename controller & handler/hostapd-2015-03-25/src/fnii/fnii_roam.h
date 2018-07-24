#ifndef _FNII_ROAM_H_
#define _FNII_ROAM_H_
#include <pthread.h>
#include "wpa_debug.h"
#include "ap/ap_config.h"

typedef char              INT8;
typedef unsigned char     UINT8;
typedef short             INT16;
typedef unsigned short    UINT16;
typedef int               INT32;
typedef unsigned int      UINT32;
typedef long int          INT64;
typedef unsigned long int UINT64;

typedef unsigned char     BOOL;

#define FNII_RET_OK   0
#define FNII_RET_ERR  -1

#define  MAC_LEN   6
#define  SSID_LEN  32

#define TRUE    1
#define FALSE   0
#define BEAT_LIVE  1
#define BEAT_DEAD  0
#define STAION_MAX  32
#define USERID_VLAUE   32               //�ļ��ֶ�ֵ����
#define SOCKET_STATUS_ENABLE  1         //socket��״̬��ʶ  ����
#define SOCKET_STATUS_DISABLE  0        //socket��״̬��ʶ  ������



#define GET_MAC_IDF(mac) ((mac)[0]&0x2)

#define MAC_ARG(x) ((UINT8*)(x))[0],((UINT8*)(x))[1],((UINT8*)(x))[2],((UINT8*)(x))[3],((UINT8*)(x))[4],((UINT8*)(x))[5]
#define IS_LOCAL_MAC(x) ((((UINT8*)(x))[0] & 0x02) == 0x02)




#define SOCKET_RESETING  0
#define SOCKET_RESETING_OVER  1


#define LOCAL_MAC 2
#define GLOBAL_MAC 0
#define SSID_MAX  8

typedef enum
{
    DEL_IF = 1,
    NEW_IF,
    UPDATE_IF,
    TYPE_MAX
}REV_MSG_TYPE;
typedef enum
{
    RET_SUSS,
    RET_ERR,
    RET_TYPE_UNKOWN
}SEND_MSG_TYPE;

typedef struct _cmd_msg_s
{
    char      type;
    u8   bssid[ETH_ALEN];
    u8   stamac[ETH_ALEN];
}cmd_msg_s;

struct _fnii_dense_station_s;

struct _fnii_dense_station_s 
{
     char station[ETH_ALEN];
     char ap[ETH_ALEN];
     struct _fnii_dense_station_s* p_next;
};


struct _fnii_dense_station_hash_s
{
    struct _fnii_dense_station_s *station[STAION_MAX];
    INT32     station_used ;
    pthread_mutex_t fnii_sta_lock;
};



typedef struct _station_flow
{
    INT8   station_mac[MAC_LEN];
    INT32  rx_byte;
    INT32  tx_byte;
}station_flow_s;

typedef struct _update_station_
{
    INT8   ap_mac[MAC_LEN];
    INT8   sta_mac[MAC_LEN];
    INT8   ssid[SSID_LEN];
}update_station__s;


typedef struct _mac_rrpair_s
{
    INT8  vif_mac[MAC_LEN];
    INT8  sta_mac[MAC_LEN];
}mac_rrpair_s;


typedef struct _interface_info_s
{
    INT8  vif_mac[MAC_LEN];
    INT32 channel;
    INT8  ssid[SSID_LEN];
    INT8  work_mode;      //0 ����Ϊ2.4Gģʽ�� 1 ����Ϊ5Gģʽ
}interface_info_s;

typedef struct  _probe_info_s
{
    INT32   rssi;           //�յ�probe���ź�ǿ��
    INT32	au;				//�տ�ʱ��ռ����
    INT8    station_mac[MAC_LEN];
    INT32   channel;
    INT8    work_mode;   //0 ����Ϊ2.4Gģʽ�� 1 ����Ϊ5Gģʽ
}probe_info_s;

typedef struct  _beat_info_s
{
    /*����״̬��ʶ*/
    INT8     beat_status;
    /*beat ��Ϣ�����к�*/
    INT32    beat_cseq;
}beat_info_s;


typedef struct _interface_all_info
{
    INT32 vap_num;
    INT32 ssid_num;
    INT32 channel;
    INT8  *ssid[SSID_MAX];
    INT8  work_mode;      //0 ����Ϊ2.4Gģʽ�� 1 ����Ϊ5Gģʽ
}interface_all_info_s;



enum _fnii_msg
{
    FNII_MSG_OK = 100,
    FNII_MSG_ERR,
    FNII_MSG_ADD_VAP,
    FNII_MSG_DEL_VAP,
    FNII_MSG_NEW_STA,
    FNII_MSG_DEL_STA,
    FNII_MSG_FLOW_INFO,
    FNII_MSG_PROBE_INFO,
    FNII_MSG_INTERFACE_INFO,
    FNII_MSG_LOCAL_MAC,
    FNII_MSG_HEART_BEAT,
    FNII_MSG_INTERFACE_NUM,
    FNII_MSG_UPDATE_STATION = 116,
    FNII_MSG_OFFLINE_VAP ,
    FNII_MSG_MAX
};


/*====================================================
������: fnii_roam_add_vap
����  : ����hostapd��ʼ�����ã��������VAP����WLAN0_VAP_MAX
���  : conf:hostapd��ʼ��Ĭ������  
����  : conf:�������֮��ĳ�ʼ��Ĭ������
����ֵ: FNII_RET_OK   FNII_RET_ERR
����  : liyongming 
ʱ��  : 2015-09-07
======================================================*/
INT32 fnii_roam_add_vap(struct hostapd_config *conf);


/*====================================================
������: fnii_roam_init
����  : ��ȡ���ò������������ӣ����ӿ�����
���  :   
����  :   
����ֵ: FNII_RET_OK   FNII_RET_ERR
����  : liyongming 
ʱ��  : 2015-08-27
======================================================*/
INT32  fnii_roam_init();


/*====================================================
������: fnii_send_msg
����  : �������������Ϣ
���  : msg_type��->��Ϣ����
        msg_data  ->��Ϣ����
����  :   
����ֵ: FNII_RET_OK   FNII_RET_ERR
����  : liyongming 
ʱ��  : 2015-08-27
======================================================*/
INT32  fnii_send_msg(INT8 msg_info,void *send_data);


/*====================================================
������: fnii_statistics_flow_thread
����  : ѭ���̣߳�ͳ��Stations����������10s�ϱ�һ��������Ϣ��������;
        ͬʱ��������Ƿ��쳣�����������ӣ�ֱ�����ӳɹ�Ϊֹ
���  : pdata��-> NULL�߳�˽�в���
����  :   
����ֵ: NULL
����  : liyongming 
ʱ��  : 2015-08-27
======================================================*/
void * fnii_statistics_flow_thread(void *pdata);



/*====================================================
������: fnii_receive_msg_thread
����  : �����̣߳����ܿ����´��������Ϣ�����������´�Ľ����
        �ظ���������ӳ�Ľ����
���  : pdata��-> NULL�߳�˽�в���
����  :   
����ֵ: NULL
����  : liyongming 
ʱ��  : 2015-08-27
======================================================*/
void * fnii_receive_msg_thread(void *pdata);

/*====================================================
������: fnii_roam_close
����  : �ر����ӣ��ͷ���Դ
���  : 
����  :   
����ֵ: FNII_RET_OK   FNII_RET_ERR
����  : liyongming 
ʱ��  : 2015-08-27
======================================================*/
INT32  fnii_roam_close();


INT8
fnii_check_socket_status();


int fnii_dense_station_find(cmd_msg_s * addr);


int fnii_dense_station_add(cmd_msg_s * addr);





#endif
