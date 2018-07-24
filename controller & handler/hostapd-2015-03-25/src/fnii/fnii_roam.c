#include<stdlib.h>
#include<string.h>
#include<errno.h>
#include<sys/types.h>
#include<sys/socket.h>
#include<netinet/in.h>
#include<stddef.h>
#include <fcntl.h>
#include<sys/stat.h>
#include<sys/socket.h>
#include<sys/un.h>

#include "utils/includes.h"
#include "utils/common.h"
#include "ap/beacon.h"
#include "ap/ap_config.h"
#include "ap/hostapd.h"
#include "radius/radius_client.h"
#include "fnii_sta.h"
#include "fnii_roam.h"
#include "ap_survey.h"

/*��ȡ�ļ���ip��ַ*/
#define  GET_MASTERIP    "grep -r masterip /etc/hostapd/config.xml" 
/*��ȡ�ļ��ж˿ں�*/
#define  GET_MASTERPORT  "grep -r masterport /etc/hostapd/config.xml"


/*�����������ip��ַ*/
INT8     fnii_master_host[USERID_VLAUE] = {0};
/*����������Ķ˿ں�*/
INT16    fnii_master_port = 0;
/*socket״̬��ʶ��*/
pthread_mutex_t fnii_socket_mutex;
/*socket״̬��ʶ*/
INT8     fnii_socket_status = SOCKET_STATUS_DISABLE;
/*socket���*/
INT32    fnii_master_socket = -1;
/*��Ϣ��ʶ���ʶ0000-9999*/
INT32    fnii_msg_idf = 0;
/*MAC��ַ�ַ�������*/
#define LOCAL_MAC_LEN        18
#define MAC_ST_LEN           13
#define  FIELD_buf_len       16

#define  MSG_INTERFACE_ALL     360       //����interface��Ϣ����


/*��ȡ����mac��ַ*/
INT8  fnii_probe_mac[ETH_ALEN] = {0};
/*��Ϣ��cseq*/
INT32 fnii_probe_count = 0;
/*������Ϣ��Ϣ*/
beat_info_s fnii_beat_info;

interface_all_info_s interface_all_info = {0,0,0,{NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL},0};




/*====================================================
������: fnii_check_socket_status
����  : ���socket״̬��ʶ
���  :
����  : 
����ֵ: socket״̬��ʶֵ
����  : liyongming 
ʱ��  : 2015-08-28
======================================================*/
INT8
fnii_check_socket_status()
{
    INT8 status;
    pthread_mutex_lock(&fnii_socket_mutex);
    status  = fnii_socket_status ;
    pthread_mutex_unlock(&fnii_socket_mutex);
    return status;
}



struct _fnii_dense_station_hash_s g_dense_station_hash;

int
fnii_init_dense_station_hash()
{
    UINT8 hash_index = 0;
    pthread_mutex_init(&g_dense_station_hash.fnii_sta_lock,NULL);
    g_dense_station_hash.station_used = 0;
    while(hash_index < STAION_MAX)
    {
        g_dense_station_hash.station[hash_index] = NULL;
        hash_index ++;
    }
    return FNII_RET_OK;
}

int
fnii_reset_dense_station_hash(struct hostapd_iface * iface)
{
    UINT8 hash_index = 0;
    struct _fnii_dense_station_s * station = NULL;
    struct _fnii_dense_station_s * station_next = NULL;
    if(iface == NULL)
    {
        return FNII_RET_ERR;
    }
    pthread_mutex_lock(&g_dense_station_hash.fnii_sta_lock);
    g_dense_station_hash.station_used = 0;
    while(hash_index < STAION_MAX)
    {
        station = g_dense_station_hash.station[hash_index];
        g_dense_station_hash.station[hash_index] = NULL;
        
        while(station != NULL)
        {
            station_next = station->p_next;
            ieee802_11_del_station(iface, station);
            os_free(station);
            station = station_next;
        }
        hash_index ++;
    }
    pthread_mutex_unlock(&g_dense_station_hash.fnii_sta_lock);
    return FNII_RET_OK;
}



int fnii_dense_station_find(cmd_msg_s * addr)
{
    UINT8 hash_idx = 0;
    struct _fnii_dense_station_s  *station_tmp = NULL;
    if(addr == NULL)
    {
        return FNII_RET_ERR;
    }
    if(fnii_check_socket_status() != SOCKET_STATUS_ENABLE)
    {
        return FNII_RET_ERR;
    }
    pthread_mutex_lock(&g_dense_station_hash.fnii_sta_lock);
    hash_idx = addr->stamac[0] %STAION_MAX;
    station_tmp = g_dense_station_hash.station[hash_idx];
    while(station_tmp != NULL)
    {
        if(os_memcmp(station_tmp->station, addr->stamac,ETH_ALEN) == 0 &&
            os_memcmp(station_tmp->ap, addr->bssid,ETH_ALEN) == 0 )
        {
            pthread_mutex_unlock(&g_dense_station_hash.fnii_sta_lock);
            return FNII_RET_OK;
        }
        station_tmp = station_tmp->p_next;
    }
    pthread_mutex_unlock(&g_dense_station_hash.fnii_sta_lock);
    return FNII_RET_ERR;
}


int fnii_dense_station_add(cmd_msg_s * addr)
{
    UINT8 hash_idx = 0;
    struct _fnii_dense_station_s  *station_tmp = NULL;
    if(addr == NULL)
    {
        return FNII_RET_ERR;
    }
    pthread_mutex_lock(&g_dense_station_hash.fnii_sta_lock);
    
    hash_idx = addr->stamac[0] %STAION_MAX;
    station_tmp = g_dense_station_hash.station[hash_idx];
    while(station_tmp != NULL)
    {
        if(os_memcmp(station_tmp->station, addr->stamac,ETH_ALEN) == 0 &&
            os_memcmp(station_tmp->ap, addr->bssid,ETH_ALEN) == 0)
        {
            pthread_mutex_unlock(&g_dense_station_hash.fnii_sta_lock);
            return FNII_RET_ERR;
        }
        station_tmp = station_tmp->p_next;
    }
    station_tmp = (struct _fnii_dense_station_s *)os_malloc(sizeof(struct _fnii_dense_station_s ));
    os_memcpy(station_tmp->station, addr->stamac, ETH_ALEN);
    os_memcpy(station_tmp->ap, addr->bssid, ETH_ALEN);
    station_tmp->p_next = g_dense_station_hash.station[hash_idx];
    g_dense_station_hash.station[hash_idx] = station_tmp;
    g_dense_station_hash.station_used ++;
    pthread_mutex_unlock(&g_dense_station_hash.fnii_sta_lock);
    return FNII_RET_OK;
}

int fnii_dense_station_del(cmd_msg_s * addr)
{
    UINT8 hash_idx = 0;
    struct _fnii_dense_station_s  *station_tmp = NULL;
    struct _fnii_dense_station_s  *station_tmp_p = NULL;
    if(addr == NULL)
    {
        return FNII_RET_ERR;
    }
    pthread_mutex_lock(&g_dense_station_hash.fnii_sta_lock);
    hash_idx = addr->stamac[0] %STAION_MAX;
    station_tmp = g_dense_station_hash.station[hash_idx];
    while(station_tmp != NULL)
    {
        if(os_memcmp(station_tmp->station, addr->stamac,ETH_ALEN) == 0 &&
            os_memcmp(station_tmp->ap, addr->bssid,ETH_ALEN) == 0)
        {
            if(station_tmp_p == NULL)
            {
                g_dense_station_hash.station[hash_idx]  = station_tmp->p_next;
            }
            else
            {
                station_tmp_p->p_next  = station_tmp->p_next;
            }
            os_free(station_tmp);
            g_dense_station_hash.station_used  --;
            pthread_mutex_unlock(&g_dense_station_hash.fnii_sta_lock);
            return FNII_RET_OK;
        }
     
        station_tmp_p = station_tmp;
        station_tmp = station_tmp->p_next;
       
    }
    pthread_mutex_unlock(&g_dense_station_hash.fnii_sta_lock);
    return FNII_RET_ERR;
}


#define DS_LOCAL_MAC_FILE    "/sys/class/net/br-lan/address" 
#define DS_LOCAL_MAC_FILE0    "/sys/class/net/wlan0/address"
#define DS_LOCAL_MAC_FILE1    "/sys/class/net/wlan1/address" 




#define  FNII_IDF   fnii_msg_idf%9999



#define  MSG_OK_LEN   12 + 1                //ok���ĵĳ��� - 6
#define  MSG_ERR_LEN  13 + 1                 //err���ĳ��� -6
#define  MSG_FLOW_INFO_LEN   60 + 1         //�������ĵĳ��� -6
#define  MSG_DEL_STATION_LEN   80       //�ն����߱��ĳ���-6
#define  MSG_NEW_STATION_LEN   80       //�ն����߱��ĳ���-6
#define  MSG_VIF_INFO_MAX_LEN  68       //interface ��Ϣ����ĳ���
#define  MSG_VIF_STA_MAC       38       //probe���ĳ���
#define  MSG_PROBE_INFO_LEN    42       //probe���ĳ��� 37
#define  MSG_LOCAL_MAC_LEN     24       //probe���ĳ���
#define  MSG_MIN_LEN           10        //��С���ĳ���

#define  PHY_NAME_LEN         32         //interface ����󳤶�
#define  WLAN0_VAP_MAX        20        // wlan0����vap����
#define  WLAN1_VAP_MAX        16        // wlan1���vap����


/*====================================================
������: fnii_set_socket_status
����  : ����socket״̬��ʶ
���  : status->socket״̬��ʶֵ
����  : 
����ֵ: 
����  : liyongming 
ʱ��  : 2015-08-28
=====================================================*/
void
fnii_set_socket_status(INT8 status)
{
    pthread_mutex_lock(&fnii_socket_mutex);
    myprint("set socket:%d\n",status);
    fnii_socket_status  = status;
    pthread_mutex_unlock(&fnii_socket_mutex);
}


/*====================================================
������: fnii_get_master_connect_info
����  : ��ȡ��������host��port����ֵ��ȫ�ֱ���
���  :   
����  :   
����ֵ: FNII_RET_OK   FNII_RET_ERR
����  : liyongming 
ʱ��  : 2015-08-27
======================================================*/
INT32  fnii_get_master_connect_info()
{
    INT8  bufport[USERID_VLAUE] = {0};
    INT8  bufip[USERID_VLAUE] = {0};
    
    if(fnii_read_config(GET_MASTERIP,bufip) == FNII_RET_OK &&
       fnii_read_config(GET_MASTERPORT,bufport) == FNII_RET_OK)
    {
        
        memcpy(fnii_master_host,bufip,USERID_VLAUE);
        fnii_master_port = atoi(bufport);
    }
    else
    {
        myprint("fnii_get_master_connect_info\n");
        return  FNII_RET_ERR;
    }
    
    return  FNII_RET_OK;
}

/*====================================================
������: fnii_read_config
����  : ͨ���ܵ��ķ�ʽ���ļ�������ȡ���е�host�Ͷ˿�
���  : grepdata -> �ܵ��������
����  : read_data-> ��ȡ����������
����ֵ: FNII_RET_OK   FNII_RET_ERR
����  : liyongming 
ʱ��  : 2015-08-27
======================================================*/
INT32 fnii_read_config(INT8 * grepdata,INT8 * read_data)
{
//�ĸ��ֽ����10λ��(ʮ����)
#define USERID_FIELD   64
#define USERID_VLAUE_MIN   17
    
    
    INT8   buffer[USERID_FIELD] = {0};
    INT8   value[USERID_VLAUE]  = {0};
    INT32  read_len             = 0;
    FILE * read_fp              = NULL;
    INT32  tmp_counter          = 0;
    INT32  value_s              = 0;
    INT32  value_e              = 0;

    //��εĺϷ���
    if(NULL == grepdata ||
       NULL == read_data)
    {
        return FNII_RET_ERR;
    }
        
        
    memset(buffer , 0, USERID_FIELD);
    memset(value , 0, USERID_VLAUE);
    //�򿪹ܵ�
    read_fp = popen(grepdata,"r");
    if(read_fp != NULL)
    {   
        read_len = fread(buffer,sizeof(INT8),USERID_FIELD - 1,read_fp);
        
        if(read_len < USERID_VLAUE_MIN)
        {
            pclose(read_fp);
            return FNII_RET_ERR;
        }
        else
        {
            //��ȡ      <masterip>172.171.48.11</masterip>   �е�  172.171.48.11
            //��ȡ      <masterport>8888</masterport>   �е�  8888
            for(tmp_counter = 0;tmp_counter < read_len; tmp_counter ++)
            {
                if(*(buffer + tmp_counter) == '>'&& value_e == 0)
                {
                    value_s = tmp_counter + 1;
                }
                if(*(buffer + tmp_counter) == '<' && value_s != 0)
                {
                    value_e = tmp_counter;
                    break;
                }
            }
            //����ַ����ĺ�����
            if(value_s == 0
               || value_e == 0
               || value_e < value_s
               || value_e - value_s > USERID_VLAUE)
            {
                pclose(read_fp);
                return FNII_RET_ERR;
            }
            memcpy(read_data,buffer + value_s,value_e - value_s);
            pclose(read_fp);
            return FNII_RET_OK;
        }
    }
    
    return FNII_RET_OK;
}

/*====================================================
������: fnii_connect2master
����  : ����socket���ӿ�����
���  :   
����  :   
����ֵ: FNII_RET_OK   FNII_RET_ERR
����  : liyongming 
ʱ��  : 2015-08-27
======================================================*/
INT32  fnii_connect2master()
{
    struct sockaddr_in   servaddr;
    memset(&servaddr, 0, sizeof(servaddr));
    if((fnii_master_socket = socket(AF_INET, SOCK_STREAM, 0)) < 0)
    {
        myprint("socket creat failed\n");
        return FNII_RET_ERR;
    }    
    servaddr.sin_family = AF_INET;
    servaddr.sin_port = htons(fnii_master_port);
    if(inet_pton(AF_INET, fnii_master_host, &servaddr.sin_addr) <= 0)
    {
        myprint("socket inet_pton failed\n");
        return FNII_RET_ERR;
    }

     /*����Ϊ��������socket*/
     /*
    if(fcntl(fnii_master_socket, F_SETFL, O_NONBLOCK) < 0)
    {
        myprint("socket fcntl failed\n");
        return FNII_RET_ERR;
    }
   */
    if(connect(fnii_master_socket, (struct sockaddr*)&servaddr, sizeof(struct sockaddr)) < 0)
    {
        myprint("connect error: %s(errno: %d)/n",strerror(errno),errno);
        return FNII_RET_ERR;
    }
    fnii_beat_info.beat_status = BEAT_LIVE;


    return FNII_RET_OK;
}

/*====================================================
������: fnii_send_msg_ok
����  : �����������OK��Ϣ
���  : idf->�յ�����Ϣ��ʶ  
����  :   
����ֵ: FNII_RET_OK   FNII_RET_ERR
����  : liyongming 
ʱ��  : 2015-08-27
======================================================*/
#define VAP_FIELD_PS 26
INT32 fnii_send_msg_ok(INT8 * idf)
{
    INT8 msg_ok[MSG_OK_LEN + 1] = {0};
    os_memcpy(msg_ok,idf,4);
    sprintf(msg_ok + 4,"100002OK;");
    
    if(send(fnii_master_socket, msg_ok, MSG_OK_LEN, 0) < 0)
    {
        return FNII_RET_ERR;
    }

    return  FNII_RET_OK;
}

/*====================================================
������: fnii_send_msg_err
����  : �����������err��Ϣ
���  : idf->�յ�����Ϣ��ʶ  
����  :   
����ֵ: FNII_RET_OK   FNII_RET_ERR
����  : liyongming 
ʱ��  : 2015-08-27
======================================================*/
INT32 fnii_send_msg_err(INT8 * idf)
{
    INT8 msg_err[MSG_ERR_LEN + 1] = {0};
    os_memcpy(msg_err,idf,4);
    sprintf(msg_err + 4,"101003ERR;");

    if(send(fnii_master_socket, msg_err, MSG_ERR_LEN, 0) < 0)
    {
        return FNII_RET_ERR;
    }
    myprint("%s\n",msg_err);
    return  FNII_RET_OK;
}

/*====================================================
������: fnii_send_msg_flow_info
����  : �����������station������Ϣ
���  : flow_info->�������͵�station������Ϣ
����  :   
����ֵ: FNII_RET_OK   FNII_RET_ERR
����  : liyongming 
ʱ��  : 2015-08-27
======================================================*/
INT32 fnii_send_msg_flow_info(station_flow_s * flow_info)
{
    INT8 msg_flow_info[MSG_FLOW_INFO_LEN + 1]  = {0};
    if(NULL == flow_info)
    {
        return FNII_RET_ERR;
    }
    sprintf(msg_flow_info,"%04d10605012%02x%02x%02x%02x%02x%02x16%016d16%016d;",FNII_IDF,
            MAC_ARG(flow_info->station_mac),flow_info->tx_byte,flow_info->rx_byte);
    if(send(fnii_master_socket, msg_flow_info, MSG_FLOW_INFO_LEN, 0) < 0)
    {
        return FNII_RET_ERR;
    }
    //myprint("%s\n",msg_flow_info);
    return FNII_RET_OK;
}

/*====================================================
������: fnii_send_msg_del_sta
����  : �����������station���ߵ���Ϣ
���  : station_mac ->���ߵ�station mac��ַ  
����  :   
����ֵ: FNII_RET_OK   FNII_RET_ERR
����  : liyongming 
ʱ��  : 2015-08-27
======================================================*/
INT32 fnii_send_msg_del_sta(update_station__s *del_mac)
{
    INT8 msg_del_station[MSG_DEL_STATION_LEN + 2]  = {0};
    UINT8  ssid_len = 0;
    if(NULL == del_mac)
    {
        return FNII_RET_ERR;
    }
    if((ssid_len = os_strlen(del_mac->ssid)) > SSID_LEN -1)
    {
        myprint("ssid_len: %u err", ssid_len);
        return FNII_RET_ERR;
    }
    sprintf(msg_del_station,"%04d105%03u12%02x%02x%02x%02x%02x%02x12%02x%02x%02x%02x%02x%02x%02u%s;",
            FNII_IDF,30  + ssid_len,MAC_ARG(del_mac->sta_mac),MAC_ARG(del_mac->ap_mac), ssid_len,del_mac->ssid);
    if(send(fnii_master_socket, msg_del_station, MSG_DEL_STATION_LEN + 1, 0) < 0)
    {
        return FNII_RET_ERR;
    }
    myprint("%s\n",msg_del_station);
    return FNII_RET_OK;    

}

INT32
fnii_send_msg_inerface_all(interface_all_info_s *interface_all_info)
{
    INT8 msg_inteface[MSG_INTERFACE_ALL] = {0};
    INT8 msg_len_tmp[4] = {0};   //���ڼ���ssid�ܳ���
    INT32 msg_len = 0;
    INT32 i = 0;
    if(interface_all_info == NULL)
    {
        return FNII_RET_ERR;
    }
    if(interface_all_info->channel >= 36)
    {
        interface_all_info->work_mode = 1;
    }
    else
    {
        interface_all_info->work_mode = 0;
    }
    sprintf(msg_inteface,"%04d11100002%02d03%03d01%d",FNII_IDF,interface_all_info->vap_num,
            interface_all_info->channel,interface_all_info->work_mode);
    for(i = 0; i < interface_all_info->ssid_num; i ++)
    {
        sprintf(msg_inteface + os_strlen(msg_inteface),"%02d%s",
               os_strlen(interface_all_info->ssid[i]),interface_all_info->ssid[i]);
    }
    msg_len = os_strlen(msg_inteface) - 10;
    sprintf(msg_len_tmp,"%03d",msg_len);
    os_memcpy(msg_inteface + 7, msg_len_tmp, 3);
    sprintf(msg_inteface + os_strlen(msg_inteface),";");
    if(send(fnii_master_socket, msg_inteface, os_strlen(msg_inteface), 0) < 0)
    {
        return FNII_RET_ERR;
    }
    myprint("%s",msg_inteface);
    return FNII_RET_OK;
}



/*====================================================
������: fnii_send_msg_if_info
����  : �����������InterFace��ϸ��Ϣ
���  :   
����  :   
����ֵ: FNII_RET_OK   FNII_RET_ERR
����  : liyongming 
ʱ��  : 2015-08-27
======================================================*/
INT32 fnii_send_msg_if_info(interface_info_s * vif_info)
{
    INT8 vif_info_msg[64] = {0};
    INT8 ssid_len = 0;
    INT32 channel = 0;
    if(NULL == vif_info)
    {
        return FNII_RET_ERR;
    }
    channel = vif_info->channel;
    ssid_len = strlen(vif_info->ssid);
    if(channel >= 36)
    {
        vif_info->work_mode = 1;
    }
    else
    {
        vif_info->work_mode = 0;
    }
    sprintf(vif_info_msg,"%04d108%03d12%02x%02x%02x%02x%02x%02x02%02d%02d%s01%d;",FNII_IDF,
            ssid_len + 23,MAC_ARG(vif_info->vif_mac),channel,ssid_len,vif_info->ssid,
            vif_info->work_mode);
    if(send(fnii_master_socket, vif_info_msg, strlen(vif_info_msg), 0) < 0)
    {
        return FNII_RET_ERR;
    }
    myprint("%s\n",vif_info_msg);
    return FNII_RET_OK;
    
}

/*====================================================
������: fnii_send_msg_new_sta
����  : �����������station������Ϣ
���  : station_mac->����station mac��ַ
����  :   
����ֵ: FNII_RET_OK   FNII_RET_ERR
����  : liyongming 
ʱ��  : 2015-08-27
======================================================*/
INT32 fnii_send_msg_new_sta(update_station__s *new_mac)
{
    INT8 msg_new_station[MSG_NEW_STATION_LEN + 2]  = {0};
    UINT8  ssid_len = 0;
    if(NULL == new_mac)
    {
        return FNII_RET_ERR;
    }
    if((ssid_len = os_strlen(new_mac->ssid) )> SSID_LEN -1)
    {
        myprint("ssid_len: %u err", ssid_len);
        return FNII_RET_ERR;
    }
    sprintf(msg_new_station,"%04d104%03u12%02x%02x%02x%02x%02x%02x12%02x%02x%02x%02x%02x%02x%02u%s;",
            FNII_IDF,30  + ssid_len,MAC_ARG(new_mac->sta_mac),MAC_ARG(new_mac->ap_mac), ssid_len,new_mac->ssid);
    if(send(fnii_master_socket, msg_new_station, MSG_NEW_STATION_LEN + 1, 0) < 0)
    {
        return FNII_RET_ERR;
    }
    myprint("%s",msg_new_station);
    return FNII_RET_OK; 
}

/*====================================================
������: fnii_send_msg_probe_info
����  : �����������probe��Ϣ
���  : probe_info ->probe��ϸ��Ϣ  
����  :   
����ֵ: FNII_RET_OK   FNII_RET_ERR
����  : liyongming 
ʱ��  : 2015-08-27
======================================================*/
INT32 fnii_send_msg_probe_info(probe_info_s *probe_info)
{
    INT8 msg_probe_info[MSG_PROBE_INFO_LEN + 2] = {0};
    INT32 channel = 0;
    if(NULL == probe_info)
    {
        return FNII_RET_ERR;
    }
    if(os_memcmp(fnii_probe_mac,probe_info->station_mac,ETH_ALEN) == 0  &&
       fnii_probe_count < 20)
    {
        fnii_probe_count ++;
        return FNII_RET_OK;
    }
    else
    {
        os_memcpy(fnii_probe_mac,probe_info->station_mac,ETH_ALEN);
        fnii_probe_count = 1;
    }
    if(probe_info->channel >= 5180)
        channel = (probe_info->channel - 5180)/5 + 36;
    else
        channel = (probe_info->channel - 2407)/5;
    if(channel > 200 || channel < 0)
    {
        return FNII_RET_OK;
    }
    if(channel >= 36)
    {
        probe_info->work_mode = 1;
    }
    else
    {
        probe_info->work_mode = 0;
    }
	 probe_info->au = get_ap_au(probe_info->work_mode);
	//probe_info.au = get_ap_au(probe_info->work_mode);
    sprintf(msg_probe_info,"%04d10703212%02x%02x%02x%02x%02x%02x03%03d03%03d03%03d01%d;",
            FNII_IDF,MAC_ARG(probe_info->station_mac),
            probe_info->rssi,probe_info->au,channel,probe_info->work_mode);
    if(send(fnii_master_socket, msg_probe_info, MSG_PROBE_INFO_LEN + 1 , 0) < 0)
    {
        return FNII_RET_ERR;
    }
   // myprint("%s\n",msg_probe_info);
    return FNII_RET_OK;
}

/*====================================================
������: fnii_send_msg_local_mac
����  : �����������localmac��ַ
���  : local_mac ->����mac 
����  :   
����ֵ: FNII_RET_OK   FNII_RET_ERR
����  : liyongming 
ʱ��  : 2015-09-08
======================================================*/
INT32
fnii_send_msg_local_mac(INT8 *local_mac)
{
    INT8 msg_local_mac[MSG_LOCAL_MAC_LEN + 2] = {0};
    
    if(local_mac == NULL)
    {
        return FNII_RET_ERR;
    }

    sprintf(msg_local_mac,"%04d10901412%s;",FNII_IDF,local_mac);
    if(send(fnii_master_socket, msg_local_mac, MSG_LOCAL_MAC_LEN + 1 , 0) < 0)
    {
        return FNII_RET_ERR;
    }
    myprint("%s",msg_local_mac);
    return FNII_RET_OK; 
}


/*====================================================
������: fnii_send_msg_update_station
����  : ����vap��ӳ��Station
���  : local_mac ->����mac 
����  :   
����ֵ: FNII_RET_OK   FNII_RET_ERR
����  : liyongming 
ʱ��  : 2015-01-12
======================================================*/
INT32
fnii_send_msg_update_station(mac_rrpair_s *mac_rrpair)
{
    INT8 msg_vap_Station[MSG_VIF_STA_MAC + 2] = {0};
    
    if(mac_rrpair == NULL)
    {
        return FNII_RET_ERR;
    }

    sprintf(msg_vap_Station,"%04d11602812%02x%02x%02x%02x%02x%02x12%02x%02x%02x%02x%02x%02x;",
            FNII_IDF,MAC_ARG(mac_rrpair->vif_mac),MAC_ARG(mac_rrpair->sta_mac));
    if(send(fnii_master_socket, msg_vap_Station, MSG_VIF_STA_MAC + 1 , 0) < 0)
    {
        return FNII_RET_ERR;
    }
    myprint("%s",msg_vap_Station);
    return FNII_RET_OK; 
}


/*====================================================
������: fnii_send_msg_beat
����  : �������������̬��Ϣ
���  : beat_info ������Ϣ��Ϣ
����  :   
����ֵ: FNII_RET_OK   FNII_RET_ERR
����  : liyongming 
ʱ��  : 2015-09-29
======================================================*/
INT32
fnii_send_msg_beat(beat_info_s *beat_info)
{
    INT8 msg_beat[MSG_MIN_LEN + 2] = {0};
    
    if(beat_info == NULL)
    {
        return FNII_RET_ERR;
    }

    sprintf(msg_beat,"%04d110000;",beat_info->beat_cseq%9999);
    beat_info->beat_status = BEAT_DEAD;
    
    if(send(fnii_master_socket, msg_beat, MSG_MIN_LEN + 1 , 0) < 0)
    {
        return FNII_RET_ERR;
    }
    return FNII_RET_OK; 
}



/*====================================================
������: fnii_roam_add_vap
����  : ����hostapd��ʼ�����ã��������VAP����WLAN0_VAP_MAX
���  : conf:hostapd��ʼ��Ĭ������  
����  : conf:�������֮��ĳ�ʼ��Ĭ������
����ֵ: FNII_RET_OK   FNII_RET_ERR
����  : liyongming 
ʱ��  : 2015-09-07
======================================================*/
INT32
fnii_roam_add_vap(struct hostapd_config *conf)
{
    INT32  num_bss = 0;
    struct hostapd_bss_config **all, *bss;
    if(conf == NULL)
    {
        return FNII_RET_ERR;
    }
    all = os_realloc_array(conf->bss, WLAN1_VAP_MAX,
                           sizeof(struct hostapd_bss_config *));
    if (all == NULL) {
        myprint("Failed to allocate memory for multi-BSS entry");
        return FNII_RET_ERR;
    }
    
    conf->bss = all;
    for(num_bss = conf->num_bss ;num_bss < WLAN1_VAP_MAX ;num_bss ++)
    {
        bss = os_zalloc(sizeof(*bss));
        if (bss == NULL)
            return FNII_RET_ERR;
        
        conf->bss[num_bss] = bss;
        conf->last_bss = bss;
        
        hostapd_config_defaults_bss(bss);
        os_memcpy(bss,conf->bss[0],sizeof(*bss));
        bss->radius = os_zalloc(sizeof(*bss->radius));
        if (bss->radius == NULL) {
            os_free(bss);
            return FNII_RET_ERR;
        }
        sprintf(bss->iface, "wlan1-%d", num_bss);
        bss->bssid[0] += 4*num_bss - 2;
        os_free(bss->ctrl_interface);
        bss->ctrl_interface = os_strdup(conf->bss[0]->ctrl_interface);
        myprint("init vap bssid:%02X:%02X:%02X:%02X:%02X:%02X",MAC_ARG(bss->bssid));
        os_memcpy(bss->ssid.vlan, bss->iface, IFNAMSIZ + 1);
    }
    
    conf->num_bss = WLAN1_VAP_MAX;
    
    return FNII_RET_OK;
}


/*====================================================
������: fnii_roam_init
����  : ��ȡ���ò������������ӣ����ӿ�����,����wan mac��ַ��������
���  :   
����  :   
����ֵ: FNII_RET_OK   FNII_RET_ERR
����  : liyongming 
ʱ��  : 2015-08-27
======================================================*/
INT32  fnii_roam_init()
{
    pthread_mutex_init(&fnii_socket_mutex,NULL);
    fnii_init_dense_station_hash();
    if(fnii_get_master_connect_info() != FNII_RET_OK ||
       fnii_connect2master() != FNII_RET_OK)
    {
        fnii_set_socket_status(SOCKET_STATUS_DISABLE);
        close(fnii_master_socket);
        return FNII_RET_ERR;
    }
  
    return FNII_RET_OK;
}

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
INT32 fnii_send_msg(INT8 msg_type,void *msg_data)
{
    int ret = FNII_RET_OK;
    beat_info_s *beat_info = NULL;
    //��鷢����Ϣ�ĺϷ���
    if(msg_type < 100 ||
       msg_type > FNII_MSG_MAX)
    {
        myprint("type err\n");
        return FNII_RET_ERR;
    }
    if(fnii_check_socket_status() == SOCKET_STATUS_DISABLE &&
       msg_type !=  FNII_MSG_LOCAL_MAC &&
       msg_type !=  FNII_MSG_INTERFACE_INFO &&
       msg_type !=  FNII_MSG_INTERFACE_NUM)
    {
        //myprint("socket err\n");
        return FNII_RET_ERR;
    }
    if(NULL == msg_data)
    {
        return FNII_RET_ERR;
    }
    
    fnii_msg_idf ++;
    if(fnii_msg_idf > 9999)
    {
        fnii_msg_idf = 0;
    }
    
    
    switch(msg_type)
    {
        case FNII_MSG_OK:
        {
            ret = fnii_send_msg_ok((INT8 *)msg_data);
        }break;
        case FNII_MSG_ERR:
        {
            ret = fnii_send_msg_err((INT8 *)msg_data);
        }break;
        case FNII_MSG_FLOW_INFO:
        {
            ret = fnii_send_msg_flow_info((station_flow_s *)msg_data);
        }break;
        case FNII_MSG_DEL_STA:
        {
            ret = fnii_send_msg_del_sta((update_station__s *)msg_data);
        }break;
        case FNII_MSG_INTERFACE_NUM:
        {
            ret = fnii_send_msg_inerface_all((interface_all_info_s *)msg_data);
           
        }break;
        case FNII_MSG_INTERFACE_INFO:
        {
            ret = fnii_send_msg_if_info((interface_info_s *)msg_data);
        }break;
        case FNII_MSG_NEW_STA:
        {
            ret = fnii_send_msg_new_sta((update_station__s *)msg_data);
        }break;
        case FNII_MSG_PROBE_INFO:
        {
            ret = fnii_send_msg_probe_info((probe_info_s *)msg_data);
        }break;
        case FNII_MSG_LOCAL_MAC:
        {
            ret = fnii_send_msg_local_mac((INT8 *)msg_data);
        }break;
        case FNII_MSG_HEART_BEAT:
        {
            beat_info = (beat_info_s *)msg_data;
            beat_info->beat_cseq = fnii_msg_idf;
            ret = fnii_send_msg_beat((beat_info_s *)msg_data);
        }break;
        case FNII_MSG_UPDATE_STATION:
        {
            ret = fnii_send_msg_update_station((mac_rrpair_s *)msg_data);
        }break;
        default:
        {
            ret = FNII_RET_OK;
        }break;
    }

    if(ret != FNII_RET_OK)
    {
        myprint("send msg err\n");
        fnii_set_socket_status(SOCKET_STATUS_DISABLE);
        shutdown(fnii_master_socket, SHUT_RDWR);
        close(fnii_master_socket);
    }
    return ret;
}


/*====================================================
������: fnii_get_flow_info
����  : ͨ��iw�����ȡstation��������Ϣ
���  : vif ->��ȡ��InterFace�ӿ�
����  :   
����ֵ: FNII_RET_OK   FNII_RET_ERR
����  : liyongming 
ʱ��  : 2015-08-27
======================================================*/
INT32 fnii_get_flow_info(INT8 *vif)
{
#define LINE_LENGTH                 128
    FILE        *fp = NULL;                 //�ܵ��ļ����
    INT8        line[LINE_LENGTH] = {0};    //�ļ���ȡ���
    INT8        iw_cmd[LINE_LENGTH] = {0};  //���� ������
    INT8        get_info_num = 0;
    station_flow_s station_flow_info;
    INT32 sta_mac[ETH_ALEN];
    if(NULL == vif)
    {
        return FNII_RET_ERR;
    }
    
    memset(&station_flow_info,0,sizeof(station_flow_info));
    
    sprintf(iw_cmd, "iw dev %s station dump", vif);
    fp = popen(iw_cmd, "r");
    if (fp == NULL) 
    {
        return FNII_RET_ERR;
    }

    memset(line, 0, LINE_LENGTH);
    while (fgets(line, LINE_LENGTH, fp) != NULL) 
    {
        if (strncmp("Station", line, 7) == 0) 
        {
            sscanf(line, "Station %x:%x:%x:%x:%x:%x (on wlan",
                   &sta_mac[0],&sta_mac[1],
                   &sta_mac[2],&sta_mac[3],
                   &sta_mac[4],&sta_mac[5]);
            os_memcpy(station_flow_info.station_mac,sta_mac,ETH_ALEN);
            station_flow_info.station_mac[0] = sta_mac[0];
            station_flow_info.station_mac[1] = sta_mac[1];
            station_flow_info.station_mac[2] = sta_mac[2];
            station_flow_info.station_mac[3] = sta_mac[3];
            station_flow_info.station_mac[4] = sta_mac[4];
            station_flow_info.station_mac[5] = sta_mac[5];
            
            get_info_num = 0;
            while (fgets(line, LINE_LENGTH, fp) != NULL) 
            {
                if (strncmp("\ttx bytes", line, 9) == 0) 
                {   
                    //myprint("%s\n",line);
                    sscanf(line, " tx bytes:%u", &station_flow_info.tx_byte);
                    get_info_num ++;
                } 
                else if (strncmp("\trx bytes", line, 9) == 0) 
                {
                    //myprint("%s\n",line);
                    sscanf(line, " rx bytes:%u", &station_flow_info.rx_byte);
                    get_info_num ++;
                }
                if (get_info_num == 2) 
                {
                    fnii_send_msg(FNII_MSG_FLOW_INFO,(void *)&station_flow_info);
                    break;
                }
            }
        }
   
        memset(line, 0, LINE_LENGTH);
    }
    pclose(fp);
    return FNII_RET_OK;
}

/*====================================================
������: fnii_get_wan_mac
����  : ��ȡ����wan mac��ַ���͵�������
���  : 
����  :   
����ֵ: FNII_RET_OK   FNII_RET_ERR
����  : liyongming 
ʱ��  : 2015-09-08
======================================================*/
INT32
fnii_get_wan_mac(char * file)
{
    FILE * fp  = NULL;
    INT8 i     = 0;
    INT8 local_mac_tmp[LOCAL_MAC_LEN] = {0};
    INT8 local_mac[MAC_ST_LEN]     = {0};
 
    if((fp = fopen(file,"r"))  == NULL) 
    { 
        return FNII_RET_ERR; 
    } 
    fread(local_mac_tmp,sizeof(INT8),LOCAL_MAC_LEN - 1,fp);
    fclose(fp);
    for(i = 0 ;i < 6; i ++)
    {
        os_memcpy(local_mac + i*2,local_mac_tmp + i*3,2);
    }
    fnii_send_msg(FNII_MSG_LOCAL_MAC, (void *)local_mac);
    return FNII_RET_OK;
}


/*====================================================
������: fnii_update_ap_info
����  : ����֮�����������������AP��Ϣ
���  : iface->phyx���
����  :   
����ֵ: FNII_RET_OK   FNII_RET_ERR
����  : liyongming 
ʱ��  : 2015-09-08
======================================================*/
INT32
fnii_update_ap_info(struct hostapd_iface *iface)
{
    INT32 i = 0;
    struct hostapd_config *conf = iface->conf;
    struct hostapd_data *hapd   = NULL;
    struct hostapd_bss_config *bss = NULL;
    if(os_memcmp(iface->phy,"phy1",4)  == 0)
    {
        fnii_get_wan_mac(DS_LOCAL_MAC_FILE1);
    }
    else
    {
        fnii_get_wan_mac(DS_LOCAL_MAC_FILE0);
    }
    if(interface_all_info.ssid[0] != NULL)
    {
         fnii_send_msg(FNII_MSG_INTERFACE_NUM,(void*)&interface_all_info);
         return FNII_RET_OK;
    }
 #if 0
    interface_info_s vif_info;
    memset(&vif_info,0x0,sizeof(interface_info_s));
    vif_info.channel = conf->channel;

    for (i = 0; i < STAION_MAX; i++)
    {
        hapd = iface->bss[0];
        os_memcpy(vif_info.ssid,hapd->conf->ssid.ssid,hapd->conf->ssid.ssid_len);
        os_memcpy(vif_info.vif_mac,hapd->own_addr,ETH_ALEN);
        if(os_memcmp(hapd->iface->phy,"phy1",4)  == 0)
            vif_info.work_mode = 1;
        else
            vif_info.work_mode = 0;
 
        vif_info.vif_mac[0] += i *2;
        //myprint("update vap bssid:%02X:%02X:%02X:%02X:%02X:%02X",MAC_ARG(hapd->own_addr));
        fnii_send_msg(FNII_MSG_INTERFACE_INFO,(void*)&vif_info);
    }
#endif

    interface_all_info.channel = conf->channel;
    interface_all_info.vap_num = STAION_MAX;
    interface_all_info.ssid_num = conf->num_bss;
    for (i = 0; i < conf->num_bss; i++)
    {
        bss = conf->bss[i];
        interface_all_info.ssid[i] = os_zalloc(SSID_LEN +1);
        os_memcpy(interface_all_info.ssid[i],bss->ssid.ssid,bss->ssid.ssid_len);
    }

    fnii_send_msg(FNII_MSG_INTERFACE_NUM,(void*)&interface_all_info);
    
    return FNII_RET_OK;
}


/*====================================================
������: fnii_statistics_flow_thread
����  : ѭ���̣߳�ͳ��Stations����������10s�ϱ�һ��������Ϣ��������;
        ͬʱ��������Ƿ��쳣�����������ӣ�ֱ�����ӳɹ�Ϊֹ
���  : pdata��-> NULL�߳�˽�в���
����  :   
����ֵ: NULL
����  : liyongming 
ʱ��  : 2015-08-28
======================================================*/
void * fnii_statistics_flow_thread(void *pdata)
{
    myprint("fnii_statistics_flow_thread start\n");
    INT32 interface_c = 0;
    INT8  interface_buf[PHY_NAME_LEN] = {0};
    fnii_update_ap_info((struct hostapd_iface *)pdata);
    fnii_set_socket_status(SOCKET_STATUS_ENABLE);
    while(1)
    {
        if(fnii_check_socket_status() != SOCKET_STATUS_ENABLE)
        {
            if(fnii_connect2master() != FNII_RET_OK)
            {
                fnii_set_socket_status(SOCKET_STATUS_DISABLE);
                close(fnii_master_socket);
                sleep(10);
                continue;
            }
            else
            {
                //�ȷ��ͳ�ʼ��VAP��Ϣ
                fnii_reset_dense_station_hash((struct hostapd_iface *)pdata);
                fnii_update_ap_info((struct hostapd_iface *)pdata);
                ieee802_11_update_all_beacons_odin((struct hostapd_iface *)pdata,0);
                /*30��������ʼ�����*/
                sleep(30);
                ieee802_11_update_all_beacons_odin((struct hostapd_iface *)pdata,1);
                fnii_set_socket_status(SOCKET_STATUS_ENABLE);
            }
        }
        if(fnii_beat_info.beat_status == BEAT_LIVE)
        {
            for(interface_c = 0; interface_c < WLAN1_VAP_MAX;interface_c ++)
            {
                if(interface_c == 0)
                {
                    sprintf(interface_buf,"wlan1");
                }
                else
                {
                    sprintf(interface_buf,"wlan1-%d",interface_c);
                }
                fnii_get_flow_info(interface_buf);
            }
            fnii_send_msg(FNII_MSG_HEART_BEAT,(void *)&fnii_beat_info);
            sleep(10);
        }
        else
        {
            myprint("heart beat  failed \n");
            fnii_set_socket_status(SOCKET_STATUS_DISABLE);
            shutdown(fnii_master_socket, SHUT_RDWR);
            close(fnii_master_socket);
        }
    }
    return NULL;
}
void fnii_get_mac(INT8 *mac,INT8 *buf)
{
    INT8 buf_tmp[3] = {0};
    INT32 i     = 0;
    for(i = 0;i < ETH_ALEN;i ++)
    {
        os_memcpy(buf_tmp,buf,2);
        buf += 2;
        mac[i] = strtol(buf_tmp,NULL,16);
    }
    return;
}

/*====================================================
������: fnii_handle_add_vap
����  : ����add vap����������vap��
���  : pdata    -> interfaces�ӿ�
        msg_buf��-> ��Ϣ����
        msg_buf_len ->��Ϣ���ݵĳ���
����  :   
����ֵ: FNII_RET_OK   FNII_RET_ERR
����  : liyongming 
ʱ��  : 2015-08-28
======================================================*/
INT32 fnii_handle_add_vap(void *pdata,INT8 * msg_buf, INT32  msg_buf_len)
{
#define FNII_ADD_VAP_MIN_LEN  31

    cmd_msg_s cmd_info;
    INT8 buf_tmp[FIELD_buf_len] = {0}; 
    char ssid_len = 0;
    char ssid[SSID_LEN] = {0};
    int  i = 0;
    struct hostapd_data *bss  = NULL;
    struct hostapd_iface * iface = (struct hostapd_iface *)pdata;
    myprint("%s", msg_buf);
    if(NULL == msg_buf ||
       msg_buf_len < FNII_ADD_VAP_MIN_LEN ||
       pdata == NULL)
    {
        return FNII_RET_ERR;
    }

    
    cmd_info.type = NEW_IF;
    fnii_get_mac(cmd_info.stamac,msg_buf + 2);
    
    msg_buf += 14;
    msg_buf += 14;
    
    msg_buf += 3;
    os_memcpy(buf_tmp, msg_buf , 2);
    buf_tmp[3] = '\0';
    ssid_len = atoi(buf_tmp);
    if(ssid_len > SSID_LEN -1)
    {
        myprint("ssid len err:%d", ssid_len);
        return FNII_RET_ERR;
    }
    msg_buf += 2;
    os_memcpy(ssid , msg_buf, ssid_len);
    for (i = 0; i < iface->num_bss; i++)
    {
        bss = iface->bss[i];
        if(os_memcmp(ssid,bss->conf->ssid.ssid,ssid_len) == 0)
        {
            os_memcpy(cmd_info.bssid, bss->own_addr , ETH_ALEN);
            break;
        }
    }
    if(cmd_info.bssid[0] == 0x0  && cmd_info.bssid[5] == 0x0)
    {
        myprint("not find ssid\n");
        return  FNII_RET_ERR;
    }
 
    if(iface->conf->channel >= 36)
    {
        myprint("5G add sta  %s %02x%02x%02x%02x%02x%02x,station used:%d",
            ssid,MAC_ARG(cmd_info.stamac),g_dense_station_hash.station_used + 1);
    }
    else
    {
        myprint("2.4G add sta %s %02x%02x%02x%02x%02x%02x,station used:%d",
            ssid,MAC_ARG(cmd_info.stamac),g_dense_station_hash.station_used + 1);
    }
  

    return fnii_dense_station_add(&cmd_info);
   // return ieee802_11_update_beacons_odin((struct hostapd_iface *)pdata,&cmd_info);
}

/*====================================================
������: fnii_handle_del_vap
����  : ����del vap����,����ɾ��vap
���  : pdata    -> interfaces�ӿ�
        msg_buf��-> ��Ϣ����
        msg_buf_len ->��Ϣ���ݵĳ���
����  :   
����ֵ: FNII_RET_OK   FNII_RET_ERR
����  : liyongming 
ʱ��  : 2015-08-28
======================================================*/
INT32 fnii_handle_del_vap(void *pdata,INT8 * msg_buf, INT32  msg_buf_len)
{
#define FNII_DEL_VAP_MIN_LEN  28
    cmd_msg_s cmd_info;
    INT8 buf_tmp[FIELD_buf_len] = {0}; 
    char ssid_len = 0;
    char ssid[SSID_LEN] = {0};
    int  i = 0;
    struct sta_info *sta = NULL;
    INT32 ret = FNII_RET_ERR;
    struct hostapd_data *bss  = NULL;
    struct hostapd_iface * iface = (struct hostapd_iface *)pdata;
    if(NULL == msg_buf ||
       msg_buf_len <  FNII_DEL_VAP_MIN_LEN ||
       iface == NULL)
    {
        return FNII_RET_ERR;
    }
    myprint("%s", msg_buf);
    os_memset (cmd_info.bssid, 0x0 , ETH_ALEN);
    cmd_info.type = DEL_IF;
    fnii_get_mac(cmd_info.stamac,msg_buf + 2);
    
    msg_buf += 14;
    msg_buf += 14;
    msg_buf += 3;
    os_memcpy(buf_tmp, msg_buf , 2);
    buf_tmp[3] = '\0';
    ssid_len = atoi(buf_tmp);
    if(ssid_len > SSID_LEN -1)
    {
        myprint("ssid len err:%d", ssid_len);
        return FNII_RET_ERR;
    }
    msg_buf += 2;
    os_memcpy(ssid , msg_buf, ssid_len);
    for (i = 0; i < iface->num_bss; i++)
    {
        bss = iface->bss[i];
        if(os_memcmp(ssid,bss->conf->ssid.ssid,ssid_len) == 0)
        {
            os_memcpy(cmd_info.bssid, bss->own_addr , ETH_ALEN);
            break;
        }
    }
    if(cmd_info.bssid[0] == 0x0  && cmd_info.bssid[5] == 0x0)
    {
        myprint("not find ssid\n");
        return  FNII_RET_ERR;
    }

    if(iface->conf->channel >= 36)
    {
        myprint("5G del sta   ssid:%s %02x%02x%02x%02x%02x%02x,station used:%d",
            ssid,MAC_ARG(cmd_info.stamac),g_dense_station_hash.station_used - 1);
    }
    else
    {
        myprint("2.4G del sta ssid:%s %02x%02x%02x%02x%02x%02x,station used:%d",
            ssid,MAC_ARG(cmd_info.stamac),g_dense_station_hash.station_used - 1);
    }
    
    ret = fnii_dense_station_del(&cmd_info);
    ieee802_11_del_station_of_vap(bss, cmd_info.stamac);
  
    return ret;
    //return ieee802_11_update_beacons_odin((struct hostapd_iface *)pdata,&cmd_info);
}

/*====================================================
������: fnii_handle_offline_vap
����  : ����del vap����,����ɾ��vap
���  : pdata    -> interfaces�ӿ�
        msg_buf��-> ��Ϣ����
        msg_buf_len ->��Ϣ���ݵĳ���
����  :   
����ֵ: FNII_RET_OK   FNII_RET_ERR
����  : liyongming 
ʱ��  : 2016-11-10
======================================================*/
INT32 fnii_handle_offline_vap(void *pdata,INT8 * msg_buf, INT32  msg_buf_len)
{
#define FNII_DEL_VAP_MIN_LEN  28
    cmd_msg_s cmd_info;
    INT8 buf_tmp[FIELD_buf_len] = {0}; 
    char ssid_len = 0;
    char ssid[SSID_LEN] = {0};
    int  i = 0;
    struct sta_info *sta = NULL;
    struct hostapd_data *bss  = NULL;
    struct hostapd_iface * iface = (struct hostapd_iface *)pdata;
    if(NULL == msg_buf ||
       msg_buf_len <  FNII_DEL_VAP_MIN_LEN ||
       iface == NULL)
    {
        return FNII_RET_ERR;
    }
    myprint("%s", msg_buf);
    os_memset (cmd_info.bssid, 0x0 , ETH_ALEN);
    cmd_info.type = DEL_IF;
    fnii_get_mac(cmd_info.stamac,msg_buf + 2);
    
    msg_buf += 14;
    msg_buf += 14;
    msg_buf += 3;
    os_memcpy(buf_tmp, msg_buf , 2);
    buf_tmp[3] = '\0';
    ssid_len = atoi(buf_tmp);
    if(ssid_len > SSID_LEN -1)
    {
        myprint("ssid len err:%d", ssid_len);
        return FNII_RET_ERR;
    }
    msg_buf += 2;
    os_memcpy(ssid , msg_buf, ssid_len);
    for (i = 0; i < iface->num_bss; i++)
    {
        bss = iface->bss[i];
        if(os_memcmp(ssid,bss->conf->ssid.ssid,ssid_len) == 0)
        {
            os_memcpy(cmd_info.bssid, bss->own_addr , ETH_ALEN);
            break;
        }
    }
    if(cmd_info.bssid[0] == 0x0  && cmd_info.bssid[5] == 0x0)
    {
        myprint("not find ssid\n");
        return  FNII_RET_ERR;
    }

    if(iface->conf->channel >= 36)
    {
        myprint("5G offline sta   ssid:%s %02x%02x%02x%02x%02x%02x,station used:%d",
            ssid,MAC_ARG(cmd_info.stamac),g_dense_station_hash.station_used - 1);
    }
    else
    {
        myprint("2.4G offline sta ssid:%s %02x%02x%02x%02x%02x%02x,station used:%d",
            ssid,MAC_ARG(cmd_info.stamac),g_dense_station_hash.station_used - 1);
    }
   
    ieee802_11_del_offline(bss, cmd_info.stamac);
  
    return FNII_RET_OK;
}

/*====================================================
������: fnii_handle_beat
����  : ����������Ϣ
���  : cseq ������Ϣ���к�
����  :   
����ֵ: FNII_RET_OK   FNII_RET_ERR
����  : liyongming 
ʱ��  : 2015-09-29
======================================================*/
INT32 fnii_handle_beat(INT32 cseq)
{
    INT32 orig_cseq = fnii_beat_info.beat_cseq%9999;
    if(orig_cseq != cseq)
    {
        return FNII_RET_ERR;
    }
    fnii_beat_info.beat_status = BEAT_LIVE;

    return FNII_RET_OK;
}



/*====================================================
������: fnii_parse_msg
����  : �����������´������ĺϷ��ԣ��Լ���Ӧ�����ͣ�����
        ��ӳ�Ľӿڴ�����Ϣ��
���  : pdata    -> interfaces�ӿ�
        msg_buf��-> ��Ϣ����
        msg_buf_len ->��Ϣ���ݵĳ���
����  :   
����ֵ: FNII_RET_OK   FNII_RET_ERR
����  : liyongming 
ʱ��  : 2015-08-28
======================================================*/
INT32 fnii_parse_msg(void *pdata,INT8 * msg_buf,INT32 msg_buf_len)
{
    INT32 ret = FNII_RET_ERR;
    INT8 msg_tpye = 0;
    INT32 field_len = 0;
    INT8 buf_tmp[FIELD_buf_len] = {0};
    INT32 cseq = 0;
    if(msg_buf == NULL ||
       msg_buf_len < MSG_MIN_LEN)
    {
        myprint("arg err\n");
        return FNII_RET_ERR;
    }
    memset(buf_tmp,0,FIELD_buf_len);
    memcpy(buf_tmp,msg_buf,4);
    cseq = atoi(buf_tmp);
    msg_buf += 4;
    //��ȡtype buf
    memset(buf_tmp,0,FIELD_buf_len);
    memcpy(buf_tmp,msg_buf,3);
    msg_tpye = atoi(buf_tmp);
    msg_buf += 3;
    //��ȡmsg�ܳ���
    memset(buf_tmp,0,FIELD_buf_len);
    memcpy(buf_tmp,msg_buf,3);
    field_len = atoi(buf_tmp);
    msg_buf += 3;
    /*head ����4+3+3��β������ 1����11;*/
    if(field_len + 11 != msg_buf_len)
    {
        //���Ȳ��Ϸ�
        myprint("len err\n");
    }
    switch(msg_tpye)
    {
        case FNII_MSG_ADD_VAP:
        {
            ret = fnii_handle_add_vap(pdata,msg_buf, field_len);
        }break;
        case FNII_MSG_DEL_VAP:
        {
            ret = fnii_handle_del_vap(pdata,msg_buf, field_len);
        }break;
        case FNII_MSG_OFFLINE_VAP:
        {
            ret = fnii_handle_offline_vap(pdata,msg_buf, field_len);
        }break;
        case FNII_MSG_HEART_BEAT:
        {
            ret = fnii_handle_beat(cseq);
        }break;
        default:
        {
            ret = FNII_RET_ERR;
        }break;
    }
    
    return ret;
}

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
void * fnii_receive_msg_thread(void *pdata)
{
    INT32  msg_len = 0;
    INT8   msg_buf[MSG_VIF_INFO_MAX_LEN *2];
    INT8   buf_tmp[FIELD_buf_len];
    INT32  msg_sg_len = 0;
    INT8  *msg_alig = msg_buf + MSG_VIF_INFO_MAX_LEN;
    while(1)
    {
        if(fnii_check_socket_status() != SOCKET_STATUS_ENABLE)
        {
            /*������������������ָ��*/
            msg_alig = msg_buf + MSG_VIF_INFO_MAX_LEN;
            /*��socket�����ý�����Ϣ���߳�˯��10��֮���ټ��*/
            sleep(10);
            continue ;
        }
        //myprint("waiting msg\n");
        os_memset(msg_buf + MSG_VIF_INFO_MAX_LEN ,0,MSG_VIF_INFO_MAX_LEN);
        msg_len = recv(fnii_master_socket, msg_buf + MSG_VIF_INFO_MAX_LEN, MSG_VIF_INFO_MAX_LEN, 0);
        
        
        if(msg_len <= 0)
        {
            /*��������ʧ�ܽ�socket״̬����Ϊdisable*/
            if(fnii_check_socket_status() == SOCKET_STATUS_ENABLE)
            {
                myprint("msg len < 0 err");
                fnii_set_socket_status(SOCKET_STATUS_DISABLE);
                shutdown(fnii_master_socket, SHUT_RDWR);
                close(fnii_master_socket);
            }
            continue ;
        }
        //myprint("rev msg %d:%s\n",msg_len,msg_buf + MSG_VIF_INFO_MAX_LEN);
        msg_len += (msg_buf + MSG_VIF_INFO_MAX_LEN - msg_alig);
        if(msg_buf + MSG_VIF_INFO_MAX_LEN != msg_alig)
        {
            myprint("slice msg %d:%s\n",msg_len,msg_alig);
        }
        while(1)
        {
            if(msg_len < 10)
            {
                break;
            }
            //��ȡmsg�ܳ���
            memset(buf_tmp,0,FIELD_buf_len);
            memcpy(buf_tmp,msg_alig + 7,3);
            msg_sg_len = atoi(buf_tmp);
 
            if(msg_sg_len > msg_len - 10)
            {
                break;
            }
            if(fnii_parse_msg(pdata,msg_alig,msg_sg_len + 11) == FNII_RET_OK)
            {
                fnii_send_msg(FNII_MSG_OK,msg_alig);
            }
            else
            {
                fnii_send_msg(FNII_MSG_ERR,msg_alig);
            }
            msg_len -= (msg_sg_len + 11);
            msg_alig += (msg_sg_len + 11);
        }
        msg_alig = msg_buf - msg_len + MSG_VIF_INFO_MAX_LEN; 
        os_memcpy(msg_alig,msg_buf + MSG_VIF_INFO_MAX_LEN * 2 - msg_len, msg_len);
    }
}

/*====================================================
������: fnii_roam_close
����  : �ر����ӣ��ͷ���Դ
���  : 
����  :   
����ֵ: FNII_RET_OK   FNII_RET_ERR
����  : liyongming 
ʱ��  : 2015-08-27
======================================================*/
INT32  fnii_roam_close()
{
    pthread_mutex_destroy(&fnii_socket_mutex);
    close(fnii_master_socket);
    return FNII_RET_OK;
}

