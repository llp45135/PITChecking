package com.rxtec.pitchecking.db;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.mongodb.Mongo;
import com.mongodb.MongoURI;
import com.rxtec.pitchecking.Config;

/**
 * Spring MongoDB configuration file
 */
@Configuration
public class SpringMongoConfig {

	public @Bean MongoTemplate mongoTemplate() throws Exception {
		return new MongoTemplate(mongo(), "pitcheck");
	}

	public Mongo mongo() throws Exception {
		String uri = "mongodb://pitcheck_writer:PitcheckWriter61336956@" + Config.getInstance().getMongoDBAddress()
				+ ":" + Config.getInstance().getMongoDBPort() + "/?authSource=pitcheck";
		return new Mongo(new MongoURI(uri));
	}

}