package kuick.slackposter

interface IHttpJsonPoster {

    suspend fun post(endpointUrl: String, json: String)

}
