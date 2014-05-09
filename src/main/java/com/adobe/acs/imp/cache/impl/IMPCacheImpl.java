package com.adobe.acs.imp.cache.impl;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adobe.acs.imp.cache.IMPCache;

/**
 * Concurrency ReadOnly Cache. Never try to write any content here. 
 * This is one way sync mechanism (only sync repository change to cache). 
 * NOTE: When using iterator, please call {@code releaseIterator()} after
 * iterating. And Also does not support {@code iterator.remove()} method.
 */

public class IMPCacheImpl<K,V> implements IMPCache<K,V>{

	private Map<K, E<V>>[] segments;
	private AtomicLong maxMem = new AtomicLong(0);
	private AtomicLong memused = new AtomicLong(21712);
	private AtomicInteger elementsNum = new AtomicInteger(0);
	private Logger log = LoggerFactory.getLogger(IMPCacheImpl.class);
	private AtomicBoolean isIterating = new AtomicBoolean(false);
	private boolean hasRemovedOldest =  false;
	
	public static class Entry<K,V> extends com.adobe.acs.imp.cache.IMPCache.Entry<K, V>{
		private K k;
		private V value;

		Entry(K key, E<V> e) {
			this.k = key;
			value = e.data;
		}
		public K getKey() {
			return k;
		}

		public V getValue() {
			return value;
		}
	}
	
	private static class E<V> {
		private final int datasize;
		private final V data;

		E(V v, int s) {
			datasize = s;
			data = v;
		}
		public int getDatasize() {
			return datasize;
		}
		
		public V getData() {
			return data;
		}
	}
	
	@SuppressWarnings({ "unchecked", "serial" })
	public
	IMPCacheImpl(long size, int lengthOfSegments) {
		maxMem.set(size);
		segments = new Map[lengthOfSegments];
		for (int i=0; i<lengthOfSegments; i++) {
			segments[i] = new LinkedHashMap<K, E<V>>(64, 0.75f, true) {
				@Override
				protected boolean removeEldestEntry(Map.Entry<K, E<V>> eldest) {
					if (isFull()) {
						log.debug("Cache is full now, remove the oldest element.");
						memused.addAndGet(-eldest.getValue().getDatasize());
						elementsNum.addAndGet(-1);
						if (!hasRemovedOldest) {
							hasRemovedOldest = true;
						}
						return true;
					}
					return false;
				}
			};
		}
	}
	
	public V get(K key) {
		int index = Math.abs(key.hashCode() % segments.length);
		Map<K,E<V>> map = segments[index];
		synchronized(map) {
			E<V> entry = map.get(key);
			if (entry != null) 
				return entry.getData();
		}
		return null;
	}

	public void put(K key, V value, int size) {
		int time = 0;
		while (isIterating.get()) {
			try {
				if (time > 1000) {
					isIterating.set(false);
					log.warn("Cache is in iterateing for more than 100s. Cache Writer thread enters anyway.");
				}
				time++;
				Thread.sleep(100);
			} catch (InterruptedException e) {
				//do nothing;
			}
		}
		E<V> entry = new E<V>(value, size);
		int index =  Math.abs(key.hashCode() % segments.length);
		Map<K,E<V>> map = segments[index];
		memused.addAndGet(size);
		elementsNum.addAndGet(1);
		synchronized(map) {
			map.put(key, entry);
		}
	}

	public boolean containsKey(K key) {
		for (int i=0; i<segments.length; i++) {
			synchronized(segments[i]) {
				if (segments[i].containsKey(key)) {
					return true;
				}
			}
		}
		return false;
	}
	
	public boolean isFull() {
		return memused.get() >= maxMem.get();
	}

	public long getMemoryUsed() {
		return memused.get();
	}

	public int size() {
		return elementsNum.get();
	}

	@Override
	public boolean hasRemoveOldest() {
		return hasRemovedOldest;
	}
	
	public void releaseIterator() {
		isIterating.set(false);
	}

	@Override
	public Iterator<com.adobe.acs.imp.cache.IMPCache.Entry<K, V>> iterator() {
		int time = 0;
		while (isIterating.get()) {
			try {
				if (time > 100) {
					isIterating.set(false);
					log.warn("Cache is in iterateing for more than 10s. Another Cache Iterator thread enters anyway.");
				}
				time++;
				Thread.sleep(100);
			} catch (InterruptedException e) {
				//do nothing;
			}
		}
		if (segments == null || segments.length == 0) {
			return null;
		}
		isIterating.set(true);
		Iterator<com.adobe.acs.imp.cache.IMPCache.Entry<K, V>> iter = new Iterator<com.adobe.acs.imp.cache.IMPCache.Entry<K, V>> () {
			int count = 0;
			private int segmentIndex = 1;
			Iterator<java.util.Map.Entry<K, E<V>>> currentIter = segments[0].entrySet().iterator();
			public boolean hasNext() {
				if (count >= size()) {
					isIterating.set(false);
					return false;
				}
				return true;
			}

			@Override
			public com.adobe.acs.imp.cache.IMPCache.Entry<K, V> next() {
				count++;
				while (currentIter.hasNext() || segmentIndex < segments.length) {
					if (currentIter.hasNext()) {
						java.util.Map.Entry<K, E<V>> item = currentIter.next();
						return new Entry<K, V>(item.getKey(),item.getValue());
					}
					else {
						currentIter = segments[segmentIndex].entrySet().iterator();
						segmentIndex ++;
					}
				}
				isIterating.set(false);
				return null;
			}

			@Override
			public void remove() {
				//do not support, read only iterator.
			}
		};
		return iter;
	}

}
