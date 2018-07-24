
// ########################################
#include <unistd.h>
#include "ap_survey.h"

//#include <linux/time.h>

struct timeval proc_start;
struct timeval proc_end;
// ########################################

int find_sta(char *sta_mac, int chip_index)
{
    FILE *file = NULL;    if (chip_index)
    {    file = popen("iw dev wlan1 station dump | grep Station | awk '{ORS=\",\";print $2}'", "r");
    }    else    {        file = popen("iw dev wlan0 station dump | grep Station | awk '{ORS=\",\";print $2}'", "r"); 
    }    if(NULL == file)    {        printf("[%-32s].err            = %s.\n", __func__, strerror(errno)); 
        return _ERR;
    }    char buf[256] = { 0 };    if (fgets(buf, sizeof(buf), file) != NULL)
    {        //printf("[%-32s].buf            = %s.\n", __func__, buf);
    }    pclose(file);
    
    return strstr(buf, sta_mac)? _TRUE : _FALSE;
}
int find_chipset(char *sta_mac)
{   
    int chip_index = 0;
    for (; chip_index <= 1; chip_index++)
    {
        if (_TRUE == find_sta(sta_mac, chip_index))
        {
            chip_index++;
            return chip_index;
        }
    }
    return _FALSE;
}
// ########################################

int get_parm(char *str, void *parm, int parm_len, char *flag)
{
    char *head = strstr(str, flag);
    if (NULL == head)
    {
        return _ERR;
    }

    char *cmd[MAX_CMD] = { 0 };
    snprintf(cmd, sizeof(cmd), "echo -e \"%s\" | awk '{print $2}'", head);

    FILE *file = NULL;
    file = popen(cmd, "r");
    if(NULL == file)
    {
        printf("[%-32s].err            = %s.\n", __func__, strerror(errno)); 
        return _ERR;
    }
    
    if (fgets(parm, parm_len, file) != NULL)
    {
        //printf("[%-32s].parm           = %s", __func__, parm);
    }
    
    pclose(file);
}
int get_sta_info(char *sta_mac, sta_info_t *sta_info, int chip_index)
{
    if (chip_index)
    {
        char cmd[MAX_CMD] = { 0 };
        switch (chip_index)
        {
            case 1:
                snprintf(cmd, MAX_CMD, "iw %s station get %s", "wlan0", sta_mac);
                break;
            case 2:
                snprintf(cmd, MAX_CMD, "iw %s station get %s", "wlan1", sta_mac);
                break;
            default:
                return _FALSE;
        }       
    
        FILE *file = NULL;
        file = popen(cmd, "r");
        if(NULL == file)
        {
            printf("[%-32s].err            = %s.\n", __func__, strerror(errno)); 
            return _ERR;
        }

        char buf[MAX_BUF] = { 0 };
        char *head = NULL;
        int index = 0;
        while (fgets(buf, MAX_BUF, file) != NULL)
        {
            char parm[MAX_PARM] = { 0 };
            head = buf;
            if (index == STA_INFO_SIGNAL)
            {
                get_parm(head, parm, sizeof(parm), "signal:");
                sta_info->signal = atoi(parm); 
                break;
            }
            index++;
            memset(buf, MAX_BUF, 0);
        }
        pclose(file);
        return _TRUE;
    }
    return _FALSE;
}
int get_ap_info(char *sta_mac, ap_info_t *ap_info, int chip_index)
{
    if (chip_index)
    {
        char cmd[MAX_CMD] = { 0 };
        switch (chip_index)
        {
            case 1:
                snprintf(cmd, MAX_CMD, "iw dev %s info", "wlan0", sta_mac);
                break;
            case 2:
                snprintf(cmd, MAX_CMD, "iw dev %s info", "wlan1", sta_mac);
                break;
            default:
                return _FALSE;
        }       
    
        FILE *file = NULL;
        file = popen(cmd, "r");
        if(NULL == file)
        {
            printf("[%-32s].err            = %s.\n", __func__, strerror(errno)); 
            return _ERR;
        }

        char buf[MAX_BUF] = { 0 };
        char *head = NULL;
        int index = 0;
        while (fgets(buf, MAX_BUF, file) != NULL)
        {
            char parm[MAX_PARM] = { 0 };
            head = buf;          
            switch (index)
            {
                case AP_INFO_BSSID:
                    get_parm(head, parm, sizeof(parm), "addr");
                    strncpy(ap_info->bssid, parm, MAC_BUF - 1);
                    break;
                case AP_INFO_SSID:
                    get_parm(head, parm, sizeof(parm), "ssid");
                    parm[strlen(parm) - 1] = '\0';
                    strncpy(ap_info->ssid, parm, MAX_SSID - 1);
                    break;
                default:
                    break;
            }
            index++;
            memset(buf, MAX_BUF, 0);
        }
        pclose(file);
        return _TRUE;
    }
    return _FALSE;    
}
int get_ap_survey(char *sta_mac, ap_survey_t *ap_survey)
{
    int chip_index = find_chipset(sta_mac);
    if (chip_index)
    {
        //ap_info_t  ap_info  = { { 0 }, { 0 }, 0, 0, 0};
        sta_info_t sta_info = { 0, 0, 0, 0, 0, 0};

        //get_ap_info(sta_mac , &ap_info, chip_index);
        get_sta_info(sta_mac, &sta_info, chip_index);
        FILE *file = NULL;
        switch (chip_index)
        {
            case 1:
                file = popen("iw wlan0 survey dump", "r");
                break;
            case 2:
                file = popen("iw wlan1 survey dump", "r");
                break;
            default:
                return _FALSE;
        }
        if(NULL == file)
        {
            printf("[%-32s].err            = %s.\n", __func__, strerror(errno)); 
            return _ERR;
        }

        char buf[MAX_BUF] = { 0 };
        char *head = NULL;
        int index = 0;
        
        double active   = 0;
        double busy     = 0;
        double ext_busy = 0;
        while(fgets(buf, sizeof(buf), file) != NULL)
        {
            char parm[MAX_PARM] = { 0 };
            head = buf;
            switch (index)
            {
                case AP_SURVEY_FREQUENCY:
                    get_parm(head, parm, sizeof(parm), "frequency:");
                    ap_survey->channel = atoi(parm);
                    break;
                case AP_SURVEY_CHANNEL_ACTIVE:
                    get_parm(head, parm, sizeof(parm), "time:");
                    active = atoi(parm);
                    break;
                case AP_SURVEY_CHANNEL_BUSY:
                    get_parm(head, parm, sizeof(parm), "time:");
                    busy = atoi(parm); 
                    break;
                case AP_SURVEY_CHANNEL_BUSY_EXT:
                    get_parm(head, parm, sizeof(parm), "time:");
                    ext_busy = atoi(parm);
                    break;
                default:
                    break;
            }
            index++;
            memset(buf, MAX_BUF, 0);
        }
        pclose(file);

        // debug
        //strncpy(ap_survey->ssid, ap_info.ssid, MAX_SSID - 1);
        ap_survey->rssi = sta_info.signal;
        ap_survey->utilization = (busy * 100) / active;
        return _TRUE;
    }
    return _FALSE;
}

int main11 (int argc, char *argv[])
{
    if (argc <= 2)
    {
        return _ERR;
    }
    opterr = 0;

    int ch = 0;
    char sta_mac[MAC_BUF] = { 0 };
	for (;;)
    {
		ch = getopt(argc, argv, "s:");
		if (ch < 0)
        {      
            break;
        }
        
        switch (ch)
        {
            case 's':
                strncpy(sta_mac, optarg, MAC_BUF - 1);
                if (strlen(sta_mac) != MAC_BUF - 1)
                {
                    return _ERR;
                }
                break;
            default:
                return _ERR;
        }
    }

    // DEBUG
    gettimeofday(&proc_start, NULL);
    long start = proc_start.tv_sec * 1000000 + proc_start.tv_usec;
    // DEBUG
    
    ap_survey_t ap_survey = { { 0 }, 0, 0, 0 };
    int ret = get_ap_survey(sta_mac, &ap_survey);

    // DEBUG
    gettimeofday(&proc_end, NULL);
    long end = proc_end.tv_sec * 1000000 + proc_end.tv_usec;
    printf("[%-32s].[%-4d]: time  = %ld ms.\n", __func__, __LINE__, (end - start) / 1000);
    // DEBUG
    if (_TRUE == ret)
    {
    	printf("\tSSID:\t\t\t\t%s\n",
    		ap_survey.ssid);
    	printf("\tfrequency:\t\t\t%u MHz%s\n", 
            ap_survey.channel, ap_survey.channel ? " [in use]" : "");
    	printf("\trssi:\t\t\t\t%d dBm\n",
    		ap_survey.rssi);
    	printf("\tutilization:\t\t\t%2.2f %%\n",
    		ap_survey.utilization);
    }
	return ret;
}

// ########################################


