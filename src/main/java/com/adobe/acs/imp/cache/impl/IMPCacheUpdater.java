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
import com.adobe.acs.imp.core.constants.AnalyticsJcrConstants;
import com.adobe.acs.imp.core.constants.ResourceTypeConstants;
import com.adobe.acs.imp.core.util.IMPUtil;
import com.adobe.acs.imp.identifiers.infrastructure.IMPIdentifierConstants;

@Component(immediate = true)
@Service
@Properties({
	@Property(name=EventConstants.EVENT_TOPIC, value="org/apache/sling/api/resource/Resource/*"),
	@Property(name=EventConstants.EVENT_FILTER, value="(&(path=/var/statistics/imp/traffic-reports/campaigns/*)(resourceType=imp/components/trafficAnalyticsData))")
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
			log.debug("NO Cache exist.");
			return;
		}
		String resourcePath = (String) event.getProperty(SlingConstants.PROPERTY_PATH);
		String resourceType = (String) event.getProperty(SlingConstants.PROPERTY_RESOURCE_TYPE);
		log.debug("Handle the resouce changed event: " + resourcePath);
		if (!resourcePath.startsWith(AnalyticsJcrConstants.CAMPAIGN_TRAFFIC_ANALYTICS_PATH) || 
				!resourceType.equalsIgnoreCase(ResourceTypeConstants.RESOURCE_TYPE_TRAFFIC_ANALYTICS_DATA)) {
			return;
		}
		handleTrafficDataChanged(event,resourcePath);
	}

	private void handleTrafficDataChanged(Event event, String resourcePath) {
		Session adminSession = null;
		try {
			adminSession = slingRepository.loginAdministrative(null);
		} catch (RepositoryException e1) {
			log.error("Can not get admin session.", e1);
			return;
		}
		try {
			String campaignId = getCampaignId(resourcePath);
			if (campaignId == null) 
				return;
			CachedCampaignInfo info = cacheManager.get(campaignId);
			CachedCampaignInfo newInfo = addDataToCache(campaignId, adminSession);
			if (newInfo == null) {
				return;
			}
			if (info != null) {
				newInfo.setCampaignName(info.getCampaignName());
				newInfo.setFocus(info.getFocus());
				newInfo.setLocale(info.getLocale());
				newInfo.setProductReference(info.getProductReference());
			}
			else {
				cacheManager.addAddtionalToCampaign(campaignId, newInfo, adminSession);
			}
			cacheManager.put(campaignId, newInfo);
			log.debug("Campaign "+ campaignId +" is updated(added) to cache.");
			
		}catch(Exception e) {
			log.error("",e);
		}finally {
			if (adminSession != null) {
				adminSession.logout();
			}
		}

	}

	private CachedCampaignInfo addDataToCache(String campaignId, Session adminSession) {
		if ((campaignId == null) || (campaignId.length() != IMPIdentifierConstants.CHARS_CAMPAIGN_ID))
		{
			return null;
		} else
		{
			 String campaignPath = IMPUtil.getDeepPath(AnalyticsJcrConstants.CAMPAIGN_TRAFFIC_ANALYTICS_PATH, campaignId);
			 try {
				Node node = adminSession.getNode(campaignPath);
				CachedCampaignInfo data = cacheManager.mapToCachedInfo(node);
				return data;
			} catch (RepositoryException e) {
				log.debug("Can not put campaign '" + campaignPath + "' into cache." ,e);
			}
			return null;
		}
	}

	private String getCampaignId(String resourcePath) throws Exception {
		int endindex = resourcePath.lastIndexOf("/" + AnalyticsJcrConstants.ANALYTICS_CAMPAIGN + "/");
		if (endindex < 0) {
			log.debug("Not Traffic Campaign Data Path. No need to Update cache.");
			return null;
		}
		return resourcePath.substring(endindex-LENGTH_OF_CAMPAIGNID, endindex);
	}

}
