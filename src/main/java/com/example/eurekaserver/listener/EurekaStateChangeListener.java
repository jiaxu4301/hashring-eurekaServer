package com.example.eurekaserver.listener;

import com.example.eurekaserver.util.HashRingUtil;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.shared.Application;
import com.netflix.eureka.EurekaServerContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.netflix.eureka.server.event.*;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.List;

import static com.example.eurekaserver.constant.Constant.REDIS_TOPIC;
import static com.example.eurekaserver.constant.Constant.WEBSOCKET_REDIS_KEY;

@Component
@Slf4j
public class EurekaStateChangeListener {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @EventListener
    public void listen(EurekaRegistryAvailableEvent event) {
        log.info("注册中心启动,{}", System.currentTimeMillis());
    }

    @EventListener
    public void listen(EurekaServerStartedEvent event) {
        log.info("注册中心服务端启动,{}", System.currentTimeMillis());
    }

    /**
     * 断线监听
     *
     * @param event
     */
    @EventListener
    public void listen(EurekaInstanceCanceledEvent event) {
        // 对应注册的 instanceID
        String serviceID = event.getServerId();
        String appName = event.getAppName();
        //具体的业务处理
        onEvent(appName);
    }

    @EventListener
    public void listen(EurekaInstanceRegisteredEvent event) {
        InstanceInfo instanceInfo = event.getInstanceInfo();
        String appName = instanceInfo.getAppName();
        log.info("服务{}进行注册", appName + instanceInfo.getHostName() + " " + instanceInfo.getIPAddr() + ":" + instanceInfo.getPort());
        onEvent(appName);
    }

    /**
     * 续约
     * @param event
     */
    @EventListener
    public void listen(EurekaInstanceRenewedEvent event) {
    }

    private void onEvent(String appName) {
        if (!appName.equalsIgnoreCase("ws-client")) {
            return;
        }

        List<Application> applications = EurekaServerContextHolder.getInstance().getServerContext().getRegistry().getSortedApplications();
        if (CollectionUtils.isEmpty(applications)) {
            return;
        }
        redisTemplate.delete(WEBSOCKET_REDIS_KEY);
        List<InstanceInfo> instances = applications.get(0).getInstances();
        instances.forEach(instance -> {
            String host = instance.getIPAddr() + ":" + instance.getPort();
            int hash = HashRingUtil.getHash(host);
            redisTemplate.opsForHash().put(WEBSOCKET_REDIS_KEY, Integer.toString(hash), host);
        });
        redisTemplate.convertAndSend(REDIS_TOPIC, "");
        log.info("监听到服务:{}发生变动,已发布事件到gateway", appName);
    }

}

