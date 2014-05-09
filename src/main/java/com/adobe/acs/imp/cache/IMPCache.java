package com.adobe.acs.imp.cache;

import com.adobe.acs.imp.cache.IMPCache.Entry;


public interface IMPCache<K,V> extends Iterable<Entry<K, V>>{

	V get(K key);
	void put(K key, V value, int size);
	boolean containsKey(K key);
	boolean isFull();
	long getMemoryUsed();
	int size();
	boolean hasRemoveOldest();
	void releaseIterator();
	
	public static abstract class Entry<K,V> {

		public abstract V getValue();
		
	}
}
