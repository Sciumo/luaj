This is just a quick fork of the [LuaJ project](http://sourceforge.net/projects/luaj/).

The main reason for the fork is to:

* Have the source in a convenient git repo.
* Remove the Sun Wireless Toolkit requirement.

The plan is to eventually tidy up the changes in the build.xml and
have it just test for the presense of SWT.  If it is installed, then
build the MDK version as usual, but if it is not, then just skip it
without complaining.
