package com.team1.ai;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 같은 프롬프트에 대한 응답을 잠시 들고 있는다.
 *
 * <p>박람회 태깅처럼 같은 입력이 반복해서 들어오는 호출이 많다. 한도와 응답 속도 둘 다에 이롭다.
 * 오래된 항목부터 버리는 단순 LRU 라 프로세스가 죽으면 사라진다 - 사라져도 다시 물어보면 그만이다.
 */
public class AiResponseCache {

    private record Entry(String value, Instant expiresAt) {
    }

    private final int maxSize;
    private final Duration ttl;
    private final Clock clock;
    private final Map<String, Entry> store;

    public AiResponseCache(int maxSize, Duration ttl, Clock clock) {
        this.maxSize = maxSize;
        this.ttl = ttl;
        this.clock = clock;
        this.store = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Entry> eldest) {
                return size() > AiResponseCache.this.maxSize;
            }
        };
    }

    public synchronized String get(String key) {
        if (maxSize <= 0) return null;
        Entry entry = store.get(key);
        if (entry == null) return null;
        if (clock.instant().isAfter(entry.expiresAt())) {
            store.remove(key);
            return null;
        }
        return entry.value();
    }

    public synchronized void put(String key, String value) {
        // 크기 0 이나 TTL 0 은 "캐시를 쓰지 않는다" 는 뜻이다. 담아두면 즉시 만료될 항목만 쌓인다.
        if (maxSize <= 0 || ttl.isZero() || ttl.isNegative()) return;
        store.put(key, new Entry(value, clock.instant().plus(ttl)));
    }

    public synchronized int size() {
        return store.size();
    }

    public static String keyOf(String feature, String model, String prompt) {
        return feature + "|" + model + "|" + Integer.toHexString(prompt.hashCode()) + "|" + prompt.length();
    }
}
