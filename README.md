# Longboard
This API serves as a bridge for communicating with the Upwork job market APIs and the Wave Account application APIs.

## How it works
This API only supports unauthenticated requests for endpoints that are used for performing authentication.

That is, when calling any endpoint outside of `*Login`, `*AccessToken` or `*RefreshToken` you *must* include as part
of your request, an `x-token-key` header.

While the `x-token-secret` header is not always required, it's best to include in all of them for completeness.

A typical request would look like
```http request
POST https://api.longboard.app/graphql
x-token-key: your-token-key-here
x-token-secret: your-token-secret-here
Content-Type: application/json

{
# Your Graphql request body here
}
```

## Login Flow
A login flow typically involves calling `*Login` to get the authorization URl the user needs to visit.

Once they have done so, the user will need to provide the `verifier` given to them by the external service.

Next, the appropriate call will need to be made to `*AccessToken` in order to complete the exchange.
Note that in the case of `upworkAccessToken` there is a `TokenInput` parameter. This requires the request
token details from the login step be returned as the API is stateless.

With the access token resolved, these now need to be supplied as the `x-token-key` and `x-token-secret` headers
as they are available.

## Upwork
Documentation for Upwork's API can be found [here](https://developers.upwork.com/?lang=java).

### Nuances

Here are some things you should bear in mind.

Sometimes hacks are needed

---

This API implementation depends on some classpath trickery to be able to make use of the existing Upwork 
java client libraries for communication. 

With that said, `com.Upwork.api.OAuthClient` exists as a patch of sorts and allow the token to be supplied
by the client (API consumers) via request headers, and correctly set forward those details to Upwork on each request.

This was a limitation of the existing implementation and may be fixed in future versions.

A `reference` is not an `id`.

---

Throughout their documentation the terms `reference`, `id` and `reference id` appear many times and seems to be used
interchangeably. 

**They're not always interchangeable.**

Where it says reference, this means the `reference` attribute of the entity. Be it a company, team, person, etc.
Where it says id, this means the `id` attribute of the entity. Be it a company, team, person, etc.

**N.B.** Some objects do not have an ID while, some have both, while others have both with the same value...💩💩💩


## Wave
Wave [documentation](https://developer.waveapps.com/hc/en-us/categories/360001114072-Documentation) is split into 
multiple parts, [OAuth](https://developer.waveapps.com/hc/en-us/articles/360019493652-OAuth-Guide) 
and [GrapqhQL](https://developer.waveapps.com/hc/en-us/articles/360018937431-API-Playground).
