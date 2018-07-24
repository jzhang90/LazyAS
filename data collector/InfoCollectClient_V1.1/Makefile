include $(TOPDIR)/rules.mk

PKG_NAME:=InfoCollectClient
PKG_RELEASE:=1

PKG_BUILD_DIR:=$(BUILD_DIR)/$(PKG_NAME)

include $(INCLUDE_DIR)/package.mk

define Package/InfoCollectClient
    SECTION:=utils
    CATEGORY:=Utilities
    SUBMENU:=Personal
    DEPENDS:=+libc +libpcap +libpthread
    TITLE:=Collect info
endef

define Package/InfoCollectClient/description
	InfoCollect
endef

define Build/Compile
	$(CP) ./src/* $(PKG_BUILD_DIR)
	$(call Build/Compile/Default)
endef

define Package/InfoCollectClient/install
	$(INSTALL_DIR) $(1)/usr/bin
	$(INSTALL_DIR) $(1)/etc/init.d
	#$(CP) ./files/InfoCollect $(1)/etc/init.d
	$(INSTALL_BIN) $(PKG_BUILD_DIR)/InfoCollectClient $(1)/usr/bin
endef

$(eval $(call BuildPackage,InfoCollectClient))
