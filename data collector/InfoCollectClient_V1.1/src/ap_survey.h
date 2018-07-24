
// ########################################
#ifndef AP_SURVEY_H
#define AP_SURVEY_H
// ########################################

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>

#define MAX_SSID    64
#define MAX_PARM    64
#define MAX_CMD     128
#define MAX_BUF     256
#define MAC_BUF     18
typedef struct
{
    unsigned int    inactive_time;
    int             signal;

    unsigned int    rx_byte;
    unsigned int    tx_byte;
    unsigned int    rx_packets;
    unsigned int    tx_packets;
} sta_info_t;
typedef struct
{
    char            bssid[MAC_BUF];
    char            ssid[MAX_SSID];

    unsigned int    channel;
    unsigned int    width;
    unsigned int    center;
} ap_info_t;

typedef enum
{
    STA_INFO_INACTIVE_TIME = 1,
    STA_INFO_SIGNAL = 8,
    MAX_STA_INFO_ENUM = 255
} sta_info_enum_t;
typedef enum
{
    AP_INFO_BSSID = 3,
    AP_INFO_SSID = 4,
    MAX_AP_INFO_ENUM = 255
} ap_info_enum_t;
// ########################################

typedef struct
{
    char            ssid[MAX_SSID];
    
    int             rssi;
    unsigned int    channel;
    double          utilization;
} ap_survey_t;

typedef enum
{
    AP_SURVEY_FREQUENCY = 1,
    AP_SURVEY_CHANNEL_ACTIVE,
    AP_SURVEY_CHANNEL_BUSY,
    AP_SURVEY_CHANNEL_BUSY_EXT,
    MAX_AP_SURVEY_ENUM = 255
} ap_survey_enum_t;
// ########################################

#define _TRUE       1
#define _FALSE      0
#define _ERR        -1

int get_ap_survey(char *sta_mac, ap_survey_t *ap_survey);

// ########################################
#endif
// ########################################



