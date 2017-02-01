#!/bin/bash

echo "Running DigDog Evaluation Script"
init=false
usage() {
	echo "Usage: ./Evaluate.sh [-i]"
}

# Read the flag options that were passed in when the script was run.
# Options include:
    # -i (init): If set, will re-do all initialization work, including cloning the defects4j repository, initializing the defects4j projects, and creating the classlists and jarlists for each project.
    # TODO: Add more options.
while getopts ":i" opt; do
	case $opt in
		i)
			init=true
			;;
		\?)
			echo "Unknown flag"
			usage
			exit 1
			;;
		:)
			echo "No flag" >&2
			;;
	esac
done

# Set up some fixed values to be used throughout the script
work_dir=proj
projects=("Closure")
# "Chart" "Closure" "Lang" "Math" "Time"
time_limits=(2 10 30 60 120)

# Go up one level to the directory that contains this repository
cd ..
echo "Stepping up to the containing directory"

# If the init flag is set, we want to re-start the initial process, so
# we remove the defects 4j repository if it already exists. This will trigger
# the rest of the defects4j set up as though the script were running for the first time.
if [ $init ]; then
    if [ -d "defects4j" ]; then
        echo "Init flag was set and defects4j repository existed, removing..."
        rm -rf defects4j
    fi
fi

# If there is no defects4j repository sitting alongside our randoop repository, we need to perform initial set up.
# This will always be true if we have set the initialization option.
if [ ! -d "defects4j" ] ; then

    echo "Preparing the defects4j repository..."
    # Clone the defects4j repository, and run the init script
	git clone https://github.com/rjust/defects4j
	cd defects4j
	./init.sh
    # TODO: this line doesn't do anything, I think
	export PATH=$PATH:./framework/bin

    echo "Downloading the Randoop release jar"
	# Get 3.0.8 release of randoop, which will be used as one of the test generation tools
	# TODO: figure out how to get compile a jar from our version of randoop in order to use that
	wget https://github.com/randoop/randoop/releases/download/v3.0.8/randoop-3.0.8.zip
	unzip randoop-3.0.8.zip

	# Install Perl DBI
	printf 'y\ny\n\n' | perl -MCPAN -e 'install Bundle::DBI'
else
	# If we already have the defects4j repository cloned, we just step inside
	echo "Defects4j repository already exists, assuming that set up has already been performed. If this is in error, re-run this script with the -i option"
	cd defects4j
	export PATH=$PATH:./framework/bin
fi

# Compile Defects4j projects and then run generated tests on them
#TODO: only run this if we are performing first time set up
for project in ${projects[@]}; do

	# Create working directory for running tests on Defects4j projects
	curr_dir=$work_dir$project
	test_dir=${curr_dir}/gentests
    echo "Setting directories for new project: ${project}..."
    echo "Working directory set to ${curr_dir}"
    echo "Test directory set to ${test_dir}"
    # If our project directory already exists, we remove it so we can start fresh
    if [ -d "${curr_dir}" ]; then
        echo "Working directory already existed, removing it...."
		rm -rf $curr_dir
    fi
    echo "Initializing working directory (${curr_dir})..."
	mkdir $curr_dir

	# Checkout and compile current project
	defects4j checkout -p $project -v 1b -w $curr_dir
	defects4j compile -w $curr_dir

	# Create the classlist and jar list for this project.
	# TODO: generalize build/classes
    # TODO: pull this into a function and add specific logic for each project based on project directory structure
	find $curr_dir/build/classes/ -name \*.class >${project}classlist.txt
	sed -i 's/\//\./g' ${project}classlist.txt
	sed -i 's/\(^.*build\.classes\.\)//g' ${project}classlist.txt
	sed -i 's/\.class$//g' ${project}classlist.txt
	sed -i '/\$/d' ${project}classlist.txt

	find $curr_dir -name \*.jar > ${project}jars.txt
done

# Iterate over each time limit. For each time limit, perform 10 iterations of test generation and coverage calculations with Randoop.
# TODO: integrate the other tools into the evaluation framework here
for time in ${time_limits[@]}; do
	for i in `seq 1 10`; do
		for project in ${projects[@]}; do
			echo "Performing evaluation #${i} for project ${project}..."
			
			# Set up local variables based on the project name that we are currently evaluating
			curr_dir=$work_dir$project
			test_dir=${curr_dir}/gentests
			jars=`tr '\n' ':' < ${project}jars.txt`

			# Set up the test dir
			if [ -d "${test_dir}" ]; then
				echo "Test directory ${test_dir} existed, clearing..."
				rm -rf $test_dir
			fi
			echo "Setting up test directory ${test_dir}"
			mkdir $test_dir

			# TODO: figure out why constant mining doesn't work
			# TODO: is it correct to run Randoop separately over each project, or should we somehow run it over the combination of all of them?
			echo "Running Randoop with time limit set to ${time}, project ${project} iteration #${i}"
			java -ea -classpath ${jars}${curr_dir}/build/classes:randoop-all-3.0.8.jar randoop.main.Main gentests --classlist=${project}classlist.txt --literals-level=CLASS --timelimit=20 --junit-reflection-allowed=false --junit-package-name=${curr_dir}.gentests

			# Change the generated test handlers to end with "Tests.java" So they are picked up by the ant task for running tests"
			mv $test_dir/RegressionTestDriver.java $test_dir/RegressionTests.java
			sed -i 's/RegressionTestDriver/RegressionTests/' $test_dir/RegressionTests.java
			mv $test_dir/ErrorTestDriver.java $test_dir/ErrorTests.java
			sed -i 's/ErrorTestDriver/ErrorTests/' $test_dir/ErrorTests.java

			# Package the test suite generated by Randoop (in $test_dir) to be the correct format for the defects4j coverage task
			echo "Packaging generated test suite into .tar.bz2 format"
			tar -cvf ${curr_dir}/randoop.tar $test_dir
			bzip2 ${curr_dir}/randoop.tar

			# Run the defects4j coverage task over the newly generated test suite.
			# Results are stored into results.txt, and the specific lines used to generate coverage are put into numbers.txt
			defects4j coverage -w $curr_dir -s ${curr_dir}/randoop.tar.bz2 > results.txt
			grep 'Lines total' results.txt > numbers.txt
			grep 'Lines covered' results.txt >> numbers.txt
			grep 'Conditions total' results.txt >> numbers.txt
			grep 'Conditions covered' results.txt >> numbers.txt

			# Remove everything but the digits from the numbers.txt file. This leaves a set of 4 lines,
			# displaying:
				# Total number of lines
				# Number of lines covered
				# Total number of conditions
				# Number of conditions covered
			sed -i 's/[^0-9]//g' numbers.txt
			cat numbers.txt

			# Remove test suite archive so we can generate again on the next iteration
			echo "Removing archive of the generated test suite..."
			rm "${curr_dir}/randoop.tar.bz2"
		done
	done
done