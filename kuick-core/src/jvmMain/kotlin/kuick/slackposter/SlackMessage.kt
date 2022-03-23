package kuick.slackposter

data class SlackMessage(val blocks: List<SlackMessageBlock>, val text: String = "")
