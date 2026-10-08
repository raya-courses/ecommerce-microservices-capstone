package com.microservices.pro.productservice;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductCacheEvictionListenerTest {

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache cache;

    @InjectMocks
    private ProductCacheEvictionListener listener;

    @Test
    void onProductChanged_evictsIndividualAndListKeys() {
        when(cacheManager.getCache("products")).thenReturn(cache);

        listener.onProductChanged(new ProductChangedEvent(42L, "UPDATED"));

        verify(cache).evict(42L);
        verify(cache).evict("all-summary");
    }
}
