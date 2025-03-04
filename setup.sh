#!/bin/bash

set -e

echo "Java Development Environment Setup Script"
echo "=========================================="
echo ""

# Function to check if a command exists
command_exists() {
  command -v "$1" >/dev/null 2>&1
}

# Setup asdf
setup_asdf() {
  echo "Setting up asdf..."
  
  if ! command_exists asdf; then
    echo "Installing asdf..."
    git clone https://github.com/asdf-vm/asdf.git ~/.asdf --branch v0.13.1
    
    # Add asdf to shell configuration
    SHELL_CONFIG=""
    if [[ -f "$HOME/.zshrc" ]]; then
      SHELL_CONFIG="$HOME/.zshrc"
    elif [[ -f "$HOME/.bashrc" ]]; then
      SHELL_CONFIG="$HOME/.bashrc"
    fi
    
    if [[ -n "$SHELL_CONFIG" ]]; then
      echo -e "\n# asdf configuration" >> "$SHELL_CONFIG"
      echo '. "$HOME/.asdf/asdf.sh"' >> "$SHELL_CONFIG"
      echo '. "$HOME/.asdf/completions/asdf.bash"' >> "$SHELL_CONFIG"
      echo "Added asdf to $SHELL_CONFIG"
    else
      echo "Please manually add asdf to your shell configuration."
    fi
    
    # Source asdf for current session
    . "$HOME/.asdf/asdf.sh"
  else
    echo "asdf is already installed."
  fi
  
  # Install plugins and versions from .tool-versions
  if [[ -f ".tool-versions" ]]; then
    echo "Found .tool-versions file. Installing specified tools..."
    
    # Read .tool-versions file and install each plugin and version
    while IFS= read -r line || [[ -n "$line" ]]; do
      # Skip empty lines and comments
      [[ -z "$line" || "$line" =~ ^#.* ]] && continue
      
      PLUGIN=$(echo "$line" | awk '{print $1}')
      VERSION=$(echo "$line" | awk '{print $2}')
      
      echo "Processing $PLUGIN version $VERSION"
      
      # Install plugin if not already installed
      if ! asdf plugin list | grep -q "$PLUGIN"; then
        echo "Installing $PLUGIN plugin for asdf..."
        asdf plugin add "$PLUGIN"
      else
        echo "$PLUGIN plugin for asdf is already installed."
      fi
      
      # Install version
      echo "Installing $PLUGIN $VERSION..."
      if ! asdf install "$PLUGIN" "$VERSION" 2>/dev/null; then
        echo "Failed to install $PLUGIN $VERSION. Checking for latest compatible version..."
        
        if [[ "$PLUGIN" == "java" ]]; then
          # For Java, try to find a suitable alternative
          if [[ "$VERSION" == *"graalvm"* || "$VERSION" == *"graal"* ]]; then
            NEW_VERSION=$(asdf list-all java | grep graalvm | grep java21 | tail -1)
            echo "Selected alternative GraalVM version: $NEW_VERSION"
          else
            NEW_VERSION=$(asdf list-all java | grep temurin | grep "^21" | tail -1)
            echo "Selected alternative Temurin version: $NEW_VERSION"
          fi
          
          # Update .tool-versions file with the new version
          sed -i.bak "s/java .*/java $NEW_VERSION/" .tool-versions
          rm -f .tool-versions.bak
          
          VERSION=$NEW_VERSION
          echo "Installing Java $VERSION..."
          asdf install java "$VERSION"
        else
          echo "Could not install $PLUGIN $VERSION. Please check available versions manually."
          continue
        fi
      fi
      
      # Set as global version
      asdf global "$PLUGIN" "$VERSION"
      echo "$PLUGIN $VERSION installed and set as global."
      
      # Special handling for Java
      if [[ "$PLUGIN" == "java" ]]; then
        # Update the build.gradle file if needed
        JAVA_MAJOR_VERSION=$(echo "$VERSION" | grep -oE '(java|jdk-|jdk)[0-9]{1,2}' | grep -oE '[0-9]{1,2}')
        if [[ -z "$JAVA_MAJOR_VERSION" ]]; then
          # Try another pattern
          JAVA_MAJOR_VERSION=$(echo "$VERSION" | grep -oE '^[0-9]{1,2}')
        fi
        
        if [[ -n "$JAVA_MAJOR_VERSION" && -f "build.gradle" ]]; then
          echo "Updating build.gradle to use Java $JAVA_MAJOR_VERSION..."
          sed -i.bak "s/sourceCompatibility = '[0-9]\{1,2\}'/sourceCompatibility = '$JAVA_MAJOR_VERSION'/" build.gradle
          sed -i.bak "s/sourceCompatibility = [0-9]\{1,2\}/sourceCompatibility = $JAVA_MAJOR_VERSION/" build.gradle
          sed -i.bak "s/targetCompatibility = [0-9]\{1,2\}/targetCompatibility = $JAVA_MAJOR_VERSION/" build.gradle
          rm -f build.gradle.bak
        fi
      fi
      
      # Special handling for direnv
      if [[ "$PLUGIN" == "direnv" ]]; then
        # Add direnv hook to shell configuration
        if [[ -n "$SHELL_CONFIG" ]]; then
          if ! grep -q "direnv hook" "$SHELL_CONFIG"; then
            echo -e "\n# direnv hook" >> "$SHELL_CONFIG"
            if [[ "$SHELL_CONFIG" == *"zshrc"* ]]; then
              echo 'eval "$(direnv hook zsh)"' >> "$SHELL_CONFIG"
            else
              echo 'eval "$(direnv hook bash)"' >> "$SHELL_CONFIG"
            fi
            echo "Added direnv hook to $SHELL_CONFIG"
          fi
        fi
        
        # Update .envrc if it exists
        if [[ -f ".envrc" ]]; then
          if ! grep -q "use asdf" ".envrc"; then
            echo "use asdf" >> ".envrc"
          fi
          direnv allow .
        else
          echo "use asdf" > ".envrc"
          direnv allow .
        fi
      fi
      
    done < .tool-versions
  else
    echo "No .tool-versions file found. Creating one with default Java version..."
    echo "java temurin-21.0.2+13.0.LTS" > .tool-versions
    
    # Install Java plugin
    if ! asdf plugin list | grep -q "java"; then
      echo "Installing Java plugin for asdf..."
      asdf plugin add java
    fi
    
    # Install default Java version
    echo "Installing default Java version (Temurin 21)..."
    asdf install java temurin-21.0.2+13.0.LTS
    asdf global java temurin-21.0.2+13.0.LTS
    
    echo "Default Java version installed and set as global."
  fi
}

# Main script - only use asdf
setup_asdf

echo ""
echo "Setup complete! You may need to restart your terminal or run 'source ~/.bashrc' or 'source ~/.zshrc'"
echo "to use the newly installed tools."
echo ""
echo "To verify Java installation:"
echo "java -version" 