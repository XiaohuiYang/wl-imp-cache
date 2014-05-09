package com.adobe.acs.imp.cache.impl;

import java.util.Iterator;
import java.util.List;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.jcr.api.SlingRepository;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adobe.acs.imp.cache.CacheManager;
import com.adobe.acs.imp.cache.CacheUtil;
import com.adobe.acs.imp.cache.IMPCache;
import com.adobe.acs.imp.cache.IMPCache.Entry;
import com.adobe.acs.imp.cache.data.CachedCampaignInfo;
import com.adobe.acs.imp.cache.data.CachedTrafficData;
import com.adobe.acs.imp.core.constants.AnalyticsJcrConstants;
import com.adobe.acs.imp.core.constants.CampaignCQConstants;
import com.adobe.acs.imp.core.constants.ResourceTypeConstants;
import com.day.cq.search.result.Hit;

@Component(label="IMPCache Manager", immediate=true,  metatype = true, description="IMP Cache Manager.")
@Service
public class CacheManagerImpl implements CacheManager{

	@Property(label="max memory", description="max memory size(MB), set to minus or zero to disable the cache. Default Value=30MB", longValue=30*1024*1024)
	public static final String MAX_MEMORY_SIZE = "maxmemory";
	
	private static long maxmem = 30*1024*1024; 
	
	private static final int defaultNumOfSegments = 200;
	
	private boolean isCacheTooBig = false;
	
	private Session adminSession = null;
	
	private Logger log = LoggerFactory.getLogger(CacheManagerImpl.class);
	
	@Reference
	SlingRepository slingRepository;
	
	private static IMPCache<String, CachedCampaignInfo> cache;
	
	private static final String[] additionalProperties = {CampaignCQConstants.CAMPAIGN_ID, CampaignCQConstants.TITLE, CampaignCQConstants.CAMPAIGN_PRODUCT_REFERENCE};
	

	protected void activate(ComponentContext context)
	{
		maxmem = (Long)context.getProperties().get(MAX_MEMORY_SIZE);
		if (maxmem <= 0) {
			cache = null;
			return;
		}
		cache = new IMPCacheImpl<String, CachedCampaignInfo>(maxmem, defaultNumOfSegments);
		log.debug("Begin to load Campaign Data to IMP Cache.");
		loadData2Cache();
		log.debug(cache.size() + " campaigns' traffic data is cached.");
	}

	private void addAdditionalPropertites()  {
		List<Hit> hits = CacheUtil.getAllCampaignsByQuery(adminSession,additionalProperties);
		if (hits == null) {
			return;
		}
		log.debug("Begin to add additional Properties.");
		try {
			for (Hit hit : hits) {
				String campaigId =  hit.getProperties().get(CampaignCQConstants.CAMPAIGN_ID, "");
				if (campaigId.isEmpty())
					continue;
				CachedCampaignInfo data = cache.get(campaigId);
				if ( data == null) {
					continue;
				}
				String campaigName = hit.getProperties().get(CampaignCQConstants.TITLE, "");
				String prductRefer = hit.getProperties().get(CampaignCQConstants.CAMPAIGN_PRODUCT_REFERENCE, "").intern();
				
				if (campaigName.isEmpty() || prductRefer.isEmpty() ) {
					continue;
				}
				
				data.setCampaignName(campaigName);
				data.setProductReference(prductRefer);
			}
		} catch (RepositoryException e) {
			log.error("", e);
		}
		log.debug("End to add additional Properties.");
	}

	private void loadData2Cache() {
		try {
			adminSession = slingRepository.loginAdministrative(null);
			Node reportNode = adminSession.getNode(AnalyticsJcrConstants.CAMPAIGN_TRAFFIC_ANALYTICS_PATH);
			createCacheFromNode(reportNode);
			addAdditionalPropertites();
		} catch (RepositoryException e) {
			log.error("",e);
		} catch (Exception e) {
			log.error("", e);
		}finally {
			if (adminSession != null) {
				adminSession.logout();
				adminSession = null;
			}
		}
	}

	private void createCacheFromNode(Node reportNode) {
		if (isCacheTooBig) 
			return;
		try {
			if (ResourceTypeConstants.RESOURCE_TYPE_CAMPAIGN_ANALYTICS.equalsIgnoreCase(
						reportNode.getProperty(CampaignCQConstants.SLING_RESOURCE_TYPE).getString())) {
				String campaignId = reportNode.getProperty(AnalyticsJcrConstants.CAMPAIGN_ID).getString();
				CachedCampaignInfo data = mapToCachedInfo(reportNode);
				if (data == null) {
					return;
				}
				if (cache.getMemoryUsed() + data.getSize() > maxmem) {
					isCacheTooBig = true;
					return;
				}
				List<CachedTrafficData> reports = data.getTrafficCampaignReport();
				if (reports.size() > 0 )
					cache.put(campaignId, data, data.getSize());
				return;
			}
		} catch (PathNotFoundException e) {
			try {
				log.debug("No sling:resourceType found on node: " + reportNode.getPath());
			} catch (RepositoryException e1) {
				log.error("",e);
			}
		} catch (RepositoryException e) {
			log.error("",e);
			return;
		}
		
		NodeIterator iter;
		try {
			iter = reportNode.getNodes();
		} catch (RepositoryException e) {
			log.error("",e);
			return;
		}
		while (iter.hasNext()) {
			createCacheFromNode(iter.nextNode());
		}
	}

	private void addTrafficData(CachedCampaignInfo campaign, Node reportNode) {
		try {
			if (ResourceTypeConstants.RESOURCE_TYPE_TRAFFIC_ANALYTICS_DATA.equalsIgnoreCase(
					reportNode.getProperty(CampaignCQConstants.SLING_RESOURCE_TYPE).getString())) {
				long users = reportNode.getProperty(AnalyticsJcrConstants.TRAFFIC_USERS).getLong();
				long impressions = reportNode.getProperty(AnalyticsJcrConstants.TRAFFIC_IMPRESSIONS).getLong();
				double reach = reportNode.getProperty(AnalyticsJcrConstants.TRAFFIC_REACH).getDouble();
				long clicks = reportNode.getProperty(AnalyticsJcrConstants.TRAFFIC_CLICKS).getLong();
				int date = Integer.parseInt(reportNode.getName().replaceAll("_", ""));
				CachedTrafficData traffic = new CachedTrafficData(date,(int)clicks,(int)impressions,(int)users,reach);
				campaign.getTrafficCampaignReport().add(traffic);
				return;
			}
		} catch(PathNotFoundException e) {
			try {
				log.debug("No sling:resourceType found on node: " + reportNode.getPath());
			} catch (RepositoryException e1) {
				log.error("", e);
			}
		} catch (RepositoryException e) {
			log.error("", e);
		}
		NodeIterator iter = null;
		try {
			iter = reportNode.getNodes();
		} catch (RepositoryException e) {
			log.error("",e);
			return;
		}
		while (iter.hasNext()) {
			addTrafficData(campaign, iter.nextNode());
		}
	}

	@Override
	public boolean isCachedAll() {
		return cache != null ? !cache.hasRemoveOldest():false;
	}

	@Override
	public CachedCampaignInfo get(String key) {
		return cache.get(key);
	}

	@Override
	public void put(String key, CachedCampaignInfo info) {
		cache.put(key, info, info.getSize());
	}

	@Override
	public boolean hasCache() {
		return cache != null;
	}

	@Override
	public CachedCampaignInfo mapToCachedInfo(Node campaignAnalyticsNode) {
		try {
			if (!ResourceTypeConstants.RESOURCE_TYPE_CAMPAIGN_ANALYTICS.equalsIgnoreCase(
					campaignAnalyticsNode.getProperty(CampaignCQConstants.SLING_RESOURCE_TYPE).getString())) {
				return null;
			}
			CachedCampaignInfo campaign = new CachedCampaignInfo();
			campaign.setFocus(campaignAnalyticsNode.getProperty(AnalyticsJcrConstants.FOCUS).getString().intern());
			campaign.setLocale(campaignAnalyticsNode.getProperty(AnalyticsJcrConstants.LOCALE).getString().intern());
			addTrafficData(campaign, campaignAnalyticsNode.getNode(AnalyticsJcrConstants.ANALYTICS_CAMPAIGN));
			return campaign;
		} catch (RepositoryException e) {
			log.error("", e);
		}
		return null;
	}

	@Override
	public void releaseIterator() {
		if (cache != null) {
			cache.releaseIterator();
		}
	}

	@Override
	public Iterator<Entry<String, CachedCampaignInfo>> getCacheIterator() {
		return cache.iterator();
	}
}
