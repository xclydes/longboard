# LongBoard
This utility aims to synchronize transactions generated in [Upwork](https://www.upwork.com/ab/find-work/) by a 
freelancer to [Wave](https://www.waveapps.com) using their respective APIs.
It is written in Kotlin using the [Spring Boot Framework](https://spring.io/projects/spring-boot) and copies data 
from the [Upwork API](https://developers.upwork.com/) 
to the Wave via [their API](https://developer.waveapps.com/hc/en-us).

# Configuration

Default values for the keys below can found in `src/main/resources/application.properties`

Key | Description | Type | Where to find it |
--- | --- | --- | --- |
longboard.wave.endpoint.graghql | The Wave GraphQL endpoint address | String | Wave API Documentation |
longboard.wave.endpoint.rest | The Wave REST endpoint | String | Wave API Documentation |
longboard.wave.client.key | The OAuth client key to be used with Wave | String | Wave developer portal |
longboard.wave.client.secret | The OAuth client secret to be used with Wave | String | Wave developer portal |
longboard.wave.token | The OAuth token to be used with Wave | String | By manually authorizing the OAuth client above. See Wave developer portal |
longboard.wave.business-id | The ID of the business to be updated as found in the API | String | This needs to be manually retrieved via the API |
longboard.wave.client.debug | Enables printing of requests+responses to Waves GraphQL endpoint | Boolean | |
longboard.upwork.client.key | The OAuth client key to be used with Upwork | String | Upwork Developer Portal |
longboard.upwork.client.secret | The OAuth client secret to be used with Upwork | String | Upwork Developer Portal |
longboard.upwork.token.value | The OAuth token value to be used with Upwork | String |  Upwork Developer Portal |
longboard.upwork.token.secret | The OAuth token secret to be used with Upwork | String |  Upwork Developer Portal |
longboard.upwork.params.account-ref | The users account ID on Uwpork |  String | Must be manually retrieved from the API |
longboard.upwork.params.earnings.fields | The list of fields to retrieve from Upwork when querying earnings | Comma Separated Values | See Upwork earning report endpoint |
longboard.upwork.params.account.fields | The list of field to retrieve from Upwork when querying accounts | Comma Separated Values | See upwork accounting endpoint |
longboard.map.upwork.wave.products | A map of Upwork transaction types to Wave product IDs | Map | Between the Upwork and Wave APIs |
longboard.map.upwork.wave.accounts | A map of Upwork transaction types to Wave account IDs | Map | Between the Upwork and Wave APIs |
longboard.sync.start | The earliest date (yyyy-MM-dd) from which to sync. Assumes the 1st of the current month if not set | String | |
longboard.sync.end | The earliest date (yyyy-MM-dd) from which to sync. Assumes today if not set | String | |
longboard.sync.span | The date interval to be used when batching requests. Not implemented, but fixed at 1 month. | String | |
