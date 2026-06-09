function initialize() {
    // mcp_url is set by the McpServer if it's running
    console.log("Template initialized. AI can now control me via MCP.");
}

function testAction() {
    console.log("Action triggered from UI!");
    setValue("mcp_url", "Action received at " + new Date().toLocaleTimeString());
}
