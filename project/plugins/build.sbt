libraryDependencies <++= (sbtVersion) { (sv) => Seq ( 
  "net.databinder" %% "conscript-plugin" % ("0.3.1_sbt" + sv),
  "com.github.philcali" %% "sbt-cx-docco" % ("sbt" + sv + "_0.0.5")
)}
