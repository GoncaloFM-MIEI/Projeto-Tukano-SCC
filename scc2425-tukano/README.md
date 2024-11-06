<h1>Project 1</h1>
<h2>Azure Tukano</h2>
<h3>
<p>Cloud Computing Systems 2024/2025</p>
<p>Masters Degree in Computer Science</p>
</h3>

**Members:**
<ul>
    <li>Gonçalo Mateus, nº 60333, gfe.mateus@campus.fct.unl.pt</li>
    <li>Rodrigo Grave, nº 60532, r.grave@campus.fct.unl.pt</li>
</ul>

<br>
<p>To be noted:</p>
<ul>
    <li>To toggle Redis/Postgree check <strong>azurekeys-regions.props</strong> file</li>
    <li>When generating props files with Azure Managemenet make sure to append a number<br>
        starting from <strong>1</strong> to <strong>number of regions</strong> in the
        <strong>BlobStoreConnection</strong> key. For example
        <ol>
            <li>(azurekeys-region1.props) BlobStoreConnection<strong>1</strong>=...</li>
            <li>(azurekeys-region2.props) BlobStoreConnection<strong>2</strong>=...</li>
            <li>...</li>
        </ol>
    </li>
</ul>