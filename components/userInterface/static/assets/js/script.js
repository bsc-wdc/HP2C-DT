const svg = d3.select("#map-container")
    .attr("width", window.innerWidth*2)
    .attr("height", window.innerHeight);

const svgWidth = +svg.attr("width");
const svgHeight = +svg.attr("height");

const projection = d3.geoNaturalEarth1()
    .scale(5000)
    .translate([svgWidth/3, svgHeight * 4.3]);

const path = d3.geoPath()
    .projection(projection);

const zoom = d3.zoom()
    .scaleExtent([1, 20])
    .on("zoom", zoomed);

svg.call(zoom);

function zoomed(event) {
    svg.selectAll("path").attr("transform", event.transform);
    svg.selectAll("line").attr("transform", event.transform);
    svg.selectAll("circle").attr("transform", event.transform);
}

d3.json("../../static/maps/spain.json").then(function(world) {
    const geojson = topojson.feature(world, world.objects.autonomous_regions);

    svg.append("path")
        .datum(geojson)
        .attr("class", "land")
        .attr("d", path);

    svg.append("path")
        .datum(topojson.mesh(world, world.objects.autonomous_regions, (a, b) => a !== b))
        .attr("class", "boundary")
        .attr("d", path);

    const nodes = [
        { id: "Barcelona", coordinates: [2.1734, 41.3851] },  
        { id: "Madrid", coordinates: [-3.7038, 40.4168] },     
        { id: "Sevilla", coordinates: [-5.9869, 37.3886] },    
        { id: "Bilbao", coordinates: [-2.9253, 43.263] }      
    ];

    const links = [
        { source: "Barcelona", target: "Madrid" },
        { source: "Madrid", target: "Sevilla" },
        { source: "Sevilla", target: "Bilbao" },
        { source: "Bilbao", target: "Barcelona" }
    ];

    svg.selectAll(".link")
        .data(links)
        .enter().append("line")
        .attr("class", "link")
        .attr("x1", d => projection(nodes.find(n => n.id === d.source).coordinates)[0])
        .attr("y1", d => projection(nodes.find(n => n.id === d.source).coordinates)[1])
        .attr("x2", d => projection(nodes.find(n => n.id === d.target).coordinates)[0])
        .attr("y2", d => projection(nodes.find(n => n.id === d.target).coordinates)[1])
        .style("stroke-width", 2);

    svg.selectAll(".node")
        .data(nodes)
        .enter().append("circle")
        .attr("class", "node")
        .attr("cx", d => projection(d.coordinates)[0])
        .attr("cy", d => projection(d.coordinates)[1])
        .attr("r", 3);
});
