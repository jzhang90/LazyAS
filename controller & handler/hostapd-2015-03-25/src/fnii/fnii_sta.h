/**
 * Copyright(C) 2014 fnii. All rights reserved.
 */
/*
 * fnii_sta.h
 *
 * Original Author:
 */

#ifndef _FNII_STA_H_
#define _FNII_STA_H_

void fnii_new_sta(uint8_t *mac,uint8_t *ssid_assoc,uint8_t *iface,uint8_t ch);
void fnii_del_sta(const uint8_t *mac,uint8_t *ssid_assoc,uint8_t *iface,uint8_t ch);
void fnii_change_sta(uint8_t *mac,uint8_t *mac_new);



#endif /* _FNII_STA_H_ */

