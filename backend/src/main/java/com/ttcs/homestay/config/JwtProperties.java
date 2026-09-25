package com.ttcs.homestay.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

	private String accessSecret;
	private String refreshSecret;
	private Duration accessTtl;
	private Duration refreshTtl;
	private String refreshCookieName;
	private boolean refreshCookieSecure;

	public String getAccessSecret() {
		return accessSecret;
	}

	public void setAccessSecret(String accessSecret) {
		this.accessSecret = accessSecret;
	}

	public String getRefreshSecret() {
		return refreshSecret;
	}

	public void setRefreshSecret(String refreshSecret) {
		this.refreshSecret = refreshSecret;
	}

	public Duration getAccessTtl() {
		return accessTtl;
	}

	public void setAccessTtl(Duration accessTtl) {
		this.accessTtl = accessTtl;
	}

	public Duration getRefreshTtl() {
		return refreshTtl;
	}

	public void setRefreshTtl(Duration refreshTtl) {
		this.refreshTtl = refreshTtl;
	}

	public String getRefreshCookieName() {
		return refreshCookieName;
	}

	public void setRefreshCookieName(String refreshCookieName) {
		this.refreshCookieName = refreshCookieName;
	}

	public boolean isRefreshCookieSecure() {
		return refreshCookieSecure;
	}

	public void setRefreshCookieSecure(boolean refreshCookieSecure) {
		this.refreshCookieSecure = refreshCookieSecure;
	}
}
