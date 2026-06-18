package com.hana.omnilens;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"omnilens.alert.dedupe.mode=in-memory",
		"management.health.redis.enabled=false"
})
class HanaOmniLensApiApplicationTests {

	@Test
	void contextLoads() {
	}

}
