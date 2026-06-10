function initialize() {
    // Generate a simple SVG and pass it as a data URI
    // We encode the SVG as base64 to ensure it parses correctly without special character issues
    var svgData = "data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIxMDAiIGhlaWdodD0iMTAwIj48Y2lyY2xlIGN4PSI1MCIgY3k9IjUwIiByPSI0MCIgc3Ryb2tlPSJibGFjayIgc3Ryb2tlLXdpZHRoPSI0IiBmaWxsPSJyZWQiIC8+PC9zdmc+";
    
    // Bind the generated SVG data to the Image widget with id="dynamic_svg"
    setValue("dynamic_svg", svgData);
}
