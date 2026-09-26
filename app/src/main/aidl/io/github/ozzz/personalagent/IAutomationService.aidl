package io.github.ozzz.personalagent;

import android.os.Bundle;

interface IAutomationService {
    // Reserved UserService lifecycle transaction; AIDL adds 1 to this ID.
    oneway void destroy() = 16777114;
    Bundle getIdentity() = 1;
}
