/*
 * hostapd / IEEE 802.11 Management: Beacon and Probe Request/Response
 * Copyright (c) 2002-2004, Instant802 Networks, Inc.
 * Copyright (c) 2005-2006, Devicescape Software, Inc.
 *
 * This software may be distributed under the terms of the BSD license.
 * See README for more details.
 */

#ifndef BEACON_H
#define BEACON_H
#include "utils/common.h"
#include "hostapd.h"
#include "fnii/fnii_roam.h"

struct ieee80211_mgmt;

void handle_probe_req(struct hostapd_data *hapd,
		      const struct ieee80211_mgmt *mgmt, size_t len,
		      struct hostapd_frame_info *fi);
int ieee802_11_set_beacon(struct hostapd_data *hapd);
int ieee802_11_set_beacons(struct hostapd_iface *iface);
int ieee802_11_update_beacons(struct hostapd_iface *iface);
int ieee802_11_build_ap_params(struct hostapd_data *hapd,
			       struct wpa_driver_ap_params *params);
void ieee802_11_free_ap_params(struct wpa_driver_ap_params *params);
int  ieee802_11_update_beacons_odin(struct hostapd_iface *iface,
                                                cmd_msg_s *ap_info);
int
ieee802_11_set_own_sta_odin(struct hostapd_data * bss);

int
ieee802_11_update_all_beacons_odin(struct hostapd_iface *iface,char flag);

int
ieee802_11_set_beacon_odin(struct hostapd_data * bss,char flag);

int ieee802_11_del_station(struct hostapd_iface *iface,struct _fnii_dense_station_s  *addr);
int ieee802_11_del_station_of_vap(struct hostapd_data *bss,char *addr);


#endif /* BEACON_H */
