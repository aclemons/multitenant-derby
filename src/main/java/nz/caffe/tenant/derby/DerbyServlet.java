/**
 * Copyright 2015-2019 Andrew Clemons <andrew.clemons@gmail.com>
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
import java.sql.Connection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.UncategorizedDataAccessException;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.web.servlet.HttpServletBean;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.DatabaseException;
import liquibase.exception.LiquibaseException;
import liquibase.resource.ClassLoaderResourceAccessor;

/**
 * This is a simple servlet to create a derby database for every 'tenant' to the
 * system.
 */
public final class DerbyServlet extends HttpServletBean {

	/** serial version uid. */
	private static final long serialVersionUID = 1139678897300023659L;

	private DatabaseFactory databaseFactory;

	private final Logger logger = LoggerFactory.getLogger(getClass());

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

		runLiquibase(ds);
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

	@Override
	protected void initServletBean() throws ServletException {
		super.initServletBean();

		this.databaseFactory = DatabaseFactory.getInstance();
	}

	@SuppressWarnings("resource")
	private void runLiquibase(final DataSource dataSource) {
		Connection connection = null;
		Database database = null;
		try {

			connection = DataSourceUtils.getConnection(dataSource);

			database = this.databaseFactory.findCorrectDatabaseImplementation(
					new JdbcConnection(connection));

			final Liquibase liquibase = new Liquibase("liquibase.xml",
					new ClassLoaderResourceAccessor(
							getClass().getClassLoader()),
					database);

			liquibase.update(new Contexts(), new LabelExpression());

		} catch (LiquibaseException e) {
			throw new UncategorizedDataAccessException("liquibase", e) {
				/** serial version uid. */
				private static final long serialVersionUID = -1213236830406128584L;
			};
		} finally {
			if (database != null) {
				try {
					database.close();
				} catch (final DatabaseException ex) {
					this.logger.debug("Could not close liquibase database", ex);
				} catch (final Throwable ex) {
					this.logger.debug(
							"Unexpected exception on closing liquibase database",
							ex);
				}
			}

			JdbcUtils.closeConnection(connection);
		}
	}

}
