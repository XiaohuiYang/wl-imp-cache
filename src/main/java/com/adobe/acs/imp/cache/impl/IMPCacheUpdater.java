package com.adobe.acs.imp.cache.impl;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.SlingConstants;
import org.apache.sling.jcr.api.SlingRepository;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adobe.acs.imp.cache.CacheManager;
import com.adobe.acs.imp.cache.data.CachedCampaignInfo;
import com.adobe.acs.imp.cache.data.CachedTrafficData;
import com.adobe.acs.imp.core.constants.AnalyticsJcrConstants;
import com.adobe.acs.imp.core.constants.ResourceTypeConstants;
import com.adobe.acs.imp.core.util.IMPUtil;
import com.adobe.acs.imp.identifiers.infrastructure.IMPIdentifierConstants;

@Component(immediate = true)
@Service
@Properties({
	@Property(name=EventConstants.EVENT_TOPIC, value="org/apache/sling/api/resource/Resource/*"),
	@Property(name=EventConstants.EVENT_FILTER, value="(&(path=/var/statistics/imp/traffic-reports/campaigns/*)(resourceType=imp/components/trafficAnalyticsData)")
})
public class IMPCacheUpdater implements EventHandler{

	@Reference
	private CacheManager cacheManager;
	
	@Reference
	private SlingRepository slingRepository;
	
	private final static int LENGTH_OF_CAMPAIGNID = 10;
	
	private final static Logger log = LoggerFactory.getLogger(IMPCacheUpdater.class);
	
	@Override
	public void handleEvent(Event event) {
		if (cacheManager == null || !cacheManager.hasCache()) {
			return;
		}
		String resourcePath = (String) event.getProperty(SlingConstants.PROPERTY_PATH);
		String resourceType = (String) event.getProperty(SlingConstants.PROPERTY_RESOURCE_TYPE);
		if (!resourcePath.startsWith(AnalyticsJcrConstants.CAMPAIGN_TRAFFIC_ANALYTICS_PATH) || 
				!resourceType.equalsIgnoreCase(ResourceTypeConstants.RESOURCE_TYPE_TRAFFIC_ANALYTICS_DATA)) {
			return;
		}
		handleTrafficDataChanged(event,resourcePath);
	}

	private void handleTrafficDataChanged(Event event, String resourcePath) {
		Session adminSession = null;
		try {
			String campaignId = getCampaignId(resourcePath);
			CachedCampaignInfo info = cacheManager.get(campaignId);
			if (info == null) {
				addDataToCache(campaignId, adminSession);
				return;
			}
			adminSession = slingRepository.loginAdministrative(null);
			Node node = adminSession.getNode(resourcePath);
			
			if (event.getTopic().equalsIgnoreCase(SlingConstants.TOPIC_RESOURCE_ADDED) || 
					event.getTopic().equalsIgnoreCase(SlingConstants.TOPIC_RESOURCE_CHANGED)) {
				handleChange(info, node, false);
			}
			else if (event.getTopic().equalsIgnoreCase(SlingConstants.TOPIC_RESOURCE_REMOVED)) {
				handleChange(info, node, true);
			}
			
		}catch(Exception e) {
			log.error("",e);
		}finally {
			if (adminSession != null) {
				adminSession.logout();
			}
		}

	}

	private void addDataToCache(String campaignId, Session adminSession) {
		if ((campaignId == null) || (campaignId.length() != IMPIdentifierConstants.CHARS_CAMPAIGN_ID))
		{
			return;
		} else
		{
			 String campaignPath = IMPUtil.getDeepPath(AnalyticsJcrConstants.CAMPAIGN_TRAFFIC_ANALYTICS_PATH, campaignId);
			 try {
				Node node = adminSession.getNode(campaignPath);
				CachedCampaignInfo data = cacheManager.mapToCachedInfo(node);
				cacheManager.put(campaignId, data);
			} catch (RepositoryException e) {
				log.debug("Can not put campaign '" + campaignPath + "' into cache." ,e);
			}
		}
	}

	private String getCampaignId(String resourcePath) throws Exception {
		int endindex = resourcePath.lastIndexOf("/" + AnalyticsJcrConstants.ANALYTICS_CAMPAIGN + "/");
		return resourcePath.substring(endindex-LENGTH_OF_CAMPAIGNID, endindex);
	}

	private void handleChange(CachedCampaignInfo info, Node reportNode, boolean isRemoved) throws Exception {
		int date = Integer.parseInt(reportNode.getName().replaceAll("_", ""));
		CachedTrafficData traffic = null;
		for (CachedTrafficData item : info.getTrafficCampaignReport()) {
			if (item.getDate() == date) {
				traffic = item;
			}
		}
		if (traffic == null) {
			return;
		}
		if (isRemoved)
			return;
		long users = reportNode.getProperty(AnalyticsJcrConstants.TRAFFIC_USERS).getLong();
		long impressions = reportNode.getProperty(AnalyticsJcrConstants.TRAFFIC_IMPRESSIONS).getLong();
		double reach = reportNode.getProperty(AnalyticsJcrConstants.TRAFFIC_REACH).getDouble();
		long clicks = reportNode.getProperty(AnalyticsJcrConstants.TRAFFIC_CLICKS).getLong();
		CachedTrafficData trafficUpdated = new CachedTrafficData(date, (int)clicks, (int)impressions, (int)users, reach);
		synchronized(info) {
			info.getTrafficCampaignReport().remove(traffic);
			info.getTrafficCampaignReport().add(trafficUpdated);
		}
	}

}
