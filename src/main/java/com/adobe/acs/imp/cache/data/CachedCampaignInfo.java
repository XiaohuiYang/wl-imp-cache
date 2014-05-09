package com.adobe.acs.imp.cache.data;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class CachedCampaignInfo {
	
//	private String path;
//	private String status;
//	private int startDate;
//	private int endDate;
	private String campaignName;
	private String focus;
	private String productReference;
	private String locale;
	private List<CachedTrafficData> trafficCampaignReport = new CopyOnWriteArrayList<CachedTrafficData>();

	public List<CachedTrafficData> getTrafficCampaignReport() {
		return trafficCampaignReport;
	}
	
	public int getSize() {
		return 100 + trafficCampaignReport.size()*37;
	}

	public String getCampaignName() {
		return campaignName;
	}

	public void setCampaignName(String campaignName) {
		this.campaignName = campaignName;
	}

	public String getFocus() {
		return focus;
	}

	public void setFocus(String focus) {
		this.focus = focus;
	}

	public String getProductReference() {
		return productReference;
	}

	public void setProductReference(String productReference) {
		this.productReference = productReference;
	}

	public String getLocale() {
		return locale;
	}

	public void setLocale(String locale) {
		this.locale = locale;
	}
	
}
