package com.adobe.acs.imp.cache;

import java.util.Iterator;

import javax.jcr.Node;

import com.adobe.acs.imp.cache.IMPCache.Entry;
import com.adobe.acs.imp.cache.data.CachedCampaignInfo;

/**
 * Concurrency ReadOnly Cache. Never try to write any content here. 
 * This is one way sync mechanism (only sync repository change to cache). 
 * NOTE: When using iterator, please call {@code releaseIterator()} after
 * iterating. And Also does not support {@code iterator.remove()} method.
 */
public interface CacheManager{
	
	CachedCampaignInfo get(String key);
	void put(String key, CachedCampaignInfo info);
	boolean isCachedAll();
	boolean hasCache();
	CachedCampaignInfo mapToCachedInfo(Node campaignAnalyticsNode);
	void releaseIterator();
	Iterator<Entry<String, CachedCampaignInfo>> getCacheIterator();
}
