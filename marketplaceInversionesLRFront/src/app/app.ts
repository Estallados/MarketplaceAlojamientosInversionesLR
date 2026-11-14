import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { Footer } from './core/layout/footer/footer';
import { Navbar } from './core/layout/navbar/navbar';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, Navbar, Footer],
  templateUrl: './app.html',
})
export class App {

}
