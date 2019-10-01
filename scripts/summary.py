#!/usr/bin/env python
# -*- encoding: utf-8

import os

print("""
<html>
<head>
<link rel="stylesheet" href="https://stackpath.bootstrapcdn.com/bootstrap/4.2.1/css/bootstrap.min.css" integrity="sha384-GJzZqFGwb1QTTN6wy59ffF1BuGJpLSa9DkKMp0DgiMDm4iYMj70gZWKYbI706tWS" crossorigin="anonymous">
<style>
.alert {
    margin-top: 1em;
    width: 100%
}

.container {
    max-width: 100%;
}

/* https://stackoverflow.com/q/248011/1558022 */
pre {
    white-space: pre-wrap;       /* Since CSS 2.1 */
    white-space: -moz-pre-wrap;  /* Mozilla, since 1999 */
    white-space: -pre-wrap;      /* Opera 4-6 */
    white-space: -o-pre-wrap;    /* Opera 7 */
    word-wrap: break-word;       /* Internet Explorer 5.5+ */

}
</style>

</head>

<body>
<div class="container">
""")

for f in os.listdir("output"):
    b_number = f.replace(".log", "")

    with open(os.path.join("output", f)) as infile:
        output = infile.read()

    if output.strip() == "Manifests match!":
        print(f"""
        <div class="alert alert-success" role="alert">
          {b_number}
        </div>""")
    elif "Manifests differ!" in output:
        print(f"""
        <div class="alert alert-warning" style="max-width: 18rem;">
          {b_number}
         </div>
         <pre><code>{ output }</code></pre>""")
    else:
        print(f"""
        <div class="alert alert-danger" style="max-width: 18rem;">
          {b_number}
         </div>
         <pre><code>{ output }</code></pre>""")

    #
    # print("<h3>{b_number}</h3>")

