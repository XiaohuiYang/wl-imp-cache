package com.adobe.acs.imp.cache.data;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class CachedCampaignInfo {
	
//	private String path;
//	private String status;
//	private int startDate;
//	private int endDate;
	private List<CachedTrafficData> trafficCampaignReport = new CopyOnWriteArrayList<CachedTrafficData>();

	public List<CachedTrafficData> getTrafficCampaignReport() {
		return trafficCampaignReport;
	}
	
	public int getSize() {
		return 100 + trafficCampaignReport.size()*37;
	}
	
}
