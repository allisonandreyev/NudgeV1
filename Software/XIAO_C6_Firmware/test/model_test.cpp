// Checks the firmware's gesture model against predictions made by the app.
// Build and run: see README in this folder.
#include "../GestureModel.h"
#include <fstream>
#include <sstream>
#include <iostream>

int main(int argc, char** argv) {
    std::ifstream in(argc > 1 ? argv[1] : "fixture.txt");
    if (!in) { std::cerr << "fixture not found\n"; return 2; }
    GestureModel::Init();
    std::string line;
    int checked = 0, failures = 0;
    while (std::getline(in, line)) {
        std::istringstream ss(line);
        std::string kind;
        ss >> kind;
        if (kind == "CMD") {
            GestureModel::HandleCommand(String(line.substr(4)));
        } else if (kind == "READ") {
            float v[3];
            ss >> v[0] >> v[1] >> v[2];
            GestureModel::Push(v);
            std::string tag;
            if (ss >> tag && tag == "EXPECT") {
                int k; float p;
                ss >> k >> p;
                int gk; float gp;
                if (!GestureModel::Predict(gk, gp)) { std::cerr << "no prediction\n"; return 1; }
                checked++;
                if (gk != k || std::fabs(gp - p) > 1e-3f) {
                    failures++;
                    std::printf("mismatch: app %d %.4f, firmware %d %.4f\n", k, p, gk, gp);
                }
            }
        }
    }
    std::printf("model id %u, %d predictions checked, %d mismatches\n", GestureModel::ModelId(), checked, failures);
    return (checked > 0 && failures == 0 && GestureModel::ModelId() == 777) ? 0 : 1;
}
