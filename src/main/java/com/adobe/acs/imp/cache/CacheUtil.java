package com.adobe.acs.imp.cache;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.jcr.Session;

import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleReference;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.day.cq.search.PredicateGroup;
import com.day.cq.search.Query;
import com.day.cq.search.QueryBuilder;
import com.day.cq.search.result.Hit;

public class CacheUtil {
	static Logger log =  LoggerFactory.getLogger(CacheUtil.class);
	
	public static List<Hit> getAllCampaignsByQuery(ResourceResolver resovler, String[] properties) {
		QueryBuilder builder = resovler.adaptTo(QueryBuilder.class);
		log.debug("Begin get all Campaigns' query.");
		Map<String, String> queryParams = new HashMap<String, String>();
		//queryParams.put("path", "/content/dam/imp/campaigns");
		queryParams.put("property", "sling:resourceType");
		queryParams.put("property.value", "imp/components/campaign");
		queryParams.put("properties", combine(properties));
		queryParams.put("p.hits", "selective");
		queryParams.put("p.nodedepth", "1");
		queryParams.put("p.limit", "-1");
		Query query = builder.createQuery(PredicateGroup.create(queryParams), resovler.adaptTo(Session.class));
		List<Hit> list = query.getResult().getHits();
		log.debug("End get all Campaigns' query." +  list.size());
		return list;		
	}
	
	
	public static List<Hit> getAllCampaignsByQuery(Session session, String[] properties) {
		try {
			ResourceResolverFactory factory = (ResourceResolverFactory) getOSGIService(ResourceResolverFactory.class);
			Map<String,Object> map =  new HashMap<String,Object>();
			map.put("user.jcr.session", session);
			return getAllCampaignsByQuery(factory.getResourceResolver(map), properties);
		} catch (Exception e) {
			log.error("",e);
		}
		return null;
	}

	private static String combine(String[] properties) {
		String temp = "";
		if (properties != null) {
			for (String p: properties) {
				temp += p;
			}
		}
		return temp;
	}
	
	public static Object getOSGIService(Class<?> clazz) {
		BundleContext bundleContext = BundleReference.class.cast(CacheUtil.class.getClassLoader()).getBundle().getBundleContext();
		ServiceReference serviceReference = bundleContext.getServiceReference(clazz.getName());
		return bundleContext.getService(serviceReference);
	}
}
