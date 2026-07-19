package com.hana.omniconnect;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"omni-connect.alert.dedupe.mode=in-memory",
		"management.health.redis.enabled=false"
})
class HanaOmniConnectApiApplicationTests {

	@Test
	void contextLoads() {
	}

}
