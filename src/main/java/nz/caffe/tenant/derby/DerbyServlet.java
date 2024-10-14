/*
 * Copyright 2015-2024 Andrew Clemons <andrew.clemons@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nz.caffe.tenant.derby;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.Location;
import org.flywaydb.core.api.configuration.ClassicConfiguration;
import org.springframework.dao.UncategorizedDataAccessException;
import org.springframework.web.servlet.HttpServletBean;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * This is a simple servlet to create a derby database for every 'tenant' to the
 * system.
 */
public final class DerbyServlet extends HttpServletBean {

	/** serial version uid. */
	private static final long serialVersionUID = 1139678897300023659L;

	/** cache. */
	private final ConcurrentMap<String, HikariDataSource> map = new ConcurrentHashMap<>();

	@Override
	public void destroy() {
		super.destroy();

		for (final HikariDataSource ds : this.map.values()) {
			ds.close();
		}

		this.map.clear();
	}

	@Override
	protected void doGet(final HttpServletRequest req,
			final HttpServletResponse resp)
					throws ServletException, IOException {

		final String tenantId = req.getHeader("tenant");

		if (tenantId == null || tenantId.isEmpty()) {
			resp.sendError(HttpServletResponse.SC_BAD_REQUEST,
					"No tenant specified");
		}

		@SuppressWarnings("resource")
		final DataSource ds = getDataSourceForTenant(tenantId);

		runFlyway(ds);
	}

	private HikariDataSource getDataSourceForTenant(final String tenant) {
		final HikariDataSource dataSource = this.map.get(tenant);

		if (dataSource != null) {
			return dataSource;
		}

		final HikariConfig config = new HikariConfig();
		config.setJdbcUrl("jdbc:derby:" + tenant + ";create=true");
		config.setUsername("test");
		config.setPassword("test");
		config.setMaximumPoolSize(8);

		final HikariDataSource ds = new HikariDataSource(config);

		final HikariDataSource oldValue = this.map.putIfAbsent(tenant, ds);

		if (oldValue == null) {
			return ds;
		}

		ds.close();

		return oldValue;
	}

	private static void runFlyway(final DataSource dataSource) {
		try {
			final ClassicConfiguration configuration = new ClassicConfiguration();
			configuration.setDataSource(dataSource);
			configuration.setLocations(new Location("classpath:flyway"));

			Flyway flyway = new Flyway(configuration);
			flyway.migrate();

		} catch (final FlywayException e) {
			throw new UncategorizedDataAccessException("flyway", e) {
				/** serial version uid. */
				private static final long serialVersionUID = -1213236830406128584L;
			};
		}
	}

}
